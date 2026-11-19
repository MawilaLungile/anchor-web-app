package com.anchor.web;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.anchor.domain.AppUser;
import com.anchor.domain.TaskItem;
import com.anchor.domain.TaskSlot;
import com.anchor.security.AnchorPrincipal;
import com.anchor.service.TaskService;
import com.anchor.service.UserService;

@Controller
public class TaskController {

    public static class TaskForm {
        @NotBlank(message = "What needs doing?")
        @Size(max = 200)
        private String title;

        @Size(max = 2000)
        private String notes;

        private TaskSlot slot = TaskSlot.OTHER;
        private boolean deep = true;
        private int estimateMinutes = 30;
        private String when = "today";
        private LocalDate date;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public TaskSlot getSlot() { return slot; }
        public void setSlot(TaskSlot slot) { this.slot = slot; }
        public boolean isDeep() { return deep; }
        public void setDeep(boolean deep) { this.deep = deep; }
        public int getEstimateMinutes() { return estimateMinutes; }
        public void setEstimateMinutes(int estimateMinutes) { this.estimateMinutes = estimateMinutes; }
        public String getWhen() { return when; }
        public void setWhen(String when) { this.when = when; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
    }

    public static final List<Integer> ESTIMATES = List.of(15, 30, 45, 60, 90, 120);

    private final CurrentUser current;
    private final TaskService tasks;
    private final UserService users;

    public TaskController(CurrentUser current, TaskService tasks, UserService users) {
        this.current = current;
        this.tasks = tasks;
        this.users = users;
    }

    @GetMapping("/tasks/new")
    public String create(@AuthenticationPrincipal AnchorPrincipal principal,
                         @RequestParam(defaultValue = "OTHER") TaskSlot slot, Model model) {
        AppUser user = current.of(principal);
        TaskForm form = new TaskForm();
        form.setSlot(slot);
        form.setDate(users.clockFor(user).today().plusDays(2));
        return show(model, form, null);
    }

    @GetMapping("/tasks/{id}/edit")
    public String edit(@AuthenticationPrincipal AnchorPrincipal principal, @PathVariable Long id, Model model) {
        AppUser user = current.of(principal);
        TaskItem task = tasks.get(user, id);
        LocalDate today = users.clockFor(user).today();

        TaskForm form = new TaskForm();
        form.setTitle(task.getTitle());
        form.setNotes(task.getNotes());
        form.setSlot(task.getSlot());
        form.setDeep(task.isDeep());
        form.setEstimateMinutes(task.getEstimateMinutes());
        form.setDate(task.getPlannedFor());
        form.setWhen(task.getPlannedFor().equals(today) ? "today"
                : task.getPlannedFor().equals(today.plusDays(1)) ? "tomorrow" : "date");
        return show(model, form, task.getId());
    }

    @PostMapping("/tasks")
    public String save(@AuthenticationPrincipal AnchorPrincipal principal,
                       @RequestParam(required = false) Long id,
                       @Valid @ModelAttribute("form") TaskForm form, BindingResult result,
                       Model model, RedirectAttributes flash) {
        AppUser user = current.of(principal);
        LocalDate today = users.clockFor(user).today();
        if ("date".equals(form.getWhen()) && (form.getDate() == null || form.getDate().isBefore(today))) {
            result.rejectValue("date", "past", "Pick today or a future date");
        }
        if (result.hasErrors()) {
            return show(model, form, id);
        }

        TaskItem task = id == null ? new TaskItem() : tasks.get(user, id);
        task.setTitle(form.getTitle().trim());
        task.setNotes(form.getNotes() == null || form.getNotes().isBlank() ? null : form.getNotes().trim());
        task.setSlot(form.getSlot() == null ? TaskSlot.OTHER : form.getSlot());
        task.setDeep(form.isDeep());
        task.setEstimateMinutes(ESTIMATES.contains(form.getEstimateMinutes()) ? form.getEstimateMinutes() : 30);
        task.setPlannedFor(switch (form.getWhen()) {
            case "tomorrow" -> today.plusDays(1);
            case "date" -> form.getDate();
            default -> today;
        });
        tasks.save(user, task);

        flash.addFlashAttribute("toast", id == null ? "Task added" : "Task updated");
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/delete")
    public String delete(@AuthenticationPrincipal AnchorPrincipal principal, @PathVariable Long id, RedirectAttributes flash) {
        tasks.delete(current.of(principal), id);
        flash.addFlashAttribute("toast", "Task deleted");
        return "redirect:/";
    }

    private String show(Model model, TaskForm form, Long id) {
        model.addAttribute("form", form);
        model.addAttribute("taskId", id);
        model.addAttribute("pageTitle", id == null ? "New task" : "Edit task");
        model.addAttribute("slots", TaskSlot.values());
        model.addAttribute("estimates", ESTIMATES);
        return "tasks/edit";
    }
}
