/* Anchor — progressive enhancements. Every page works without JavaScript; this adds live timers,
   confirmations and the guided breathing exercise. */
(function () {
  "use strict";

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => Array.from(root.querySelectorAll(sel));

  const pad = (n) => String(n).padStart(2, "0");
  const clock = (ms) => {
    const total = Math.max(0, Math.ceil(ms / 1000));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
  };

  /* ------------------------------------------------------------ Confirm before destructive posts */
  document.addEventListener("submit", (e) => {
    const form = e.target;
    const message = form.getAttribute("data-confirm");
    if (message && !window.confirm(message)) {
      e.preventDefault();
    }
  });

  /* ------------------------------------------------------------ Time zone on sign-up */
  const tz = $('input[name="timeZone"]');
  if (tz && !tz.value) {
    try { tz.value = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC"; } catch (_) { tz.value = "UTC"; }
  }

  /* ------------------------------------------------------------ Focus dial */
  const dial = $("#dial");
  if (dial) {
    const start = Number(dial.dataset.start);
    const end = Number(dial.dataset.end);
    const arc = $("#arc", dial);
    const glow = $("#glow", dial);
    const time = $("#time", dial);
    const ticks = $("#ticks", dial);
    const circumference = 2 * Math.PI * 112;

    // 60 dial ticks, longer every five minutes.
    const ns = "http://www.w3.org/2000/svg";
    for (let i = 0; i < 60; i++) {
      const a = (i / 60) * Math.PI * 2 - Math.PI / 2;
      const major = i % 5 === 0;
      const r1 = major ? 130 : 135;
      const line = document.createElementNS(ns, "line");
      line.setAttribute("x1", 150 + Math.cos(a) * r1);
      line.setAttribute("y1", 150 + Math.sin(a) * r1);
      line.setAttribute("x2", 150 + Math.cos(a) * 142);
      line.setAttribute("y2", 150 + Math.sin(a) * 142);
      line.setAttribute("class", "tick");
      line.setAttribute("stroke-width", major ? "2.4" : "1.2");
      ticks.appendChild(line);
    }

    [arc, glow].forEach((c) => c.setAttribute("stroke-dasharray", circumference.toFixed(2)));
    let completed = false;

    const tick = () => {
      const now = Date.now();
      const progress = Math.min(1, Math.max(0, (now - start) / (end - start)));
      const offset = (circumference * (1 - progress)).toFixed(2);
      arc.setAttribute("stroke-dashoffset", offset);
      glow.setAttribute("stroke-dashoffset", offset);
      time.textContent = clock(end - now);
      document.title = `${clock(end - now)} · Focus · Anchor`;

      if (now >= end && !completed) {
        completed = true;
        const form = $("#complete-form");
        if (form) form.submit();
      }
    };
    tick();
    setInterval(tick, 500);
  }

  /* ------------------------------------------------------------ Focus setup: pick a task */
  const intention = $("#intention");
  $$('input[name="taskId"]').forEach((radio) => {
    radio.addEventListener("change", () => {
      if (intention && radio.dataset.title) intention.value = radio.dataset.title;
      const minutes = radio.dataset.minutes && $(`input[name="minutes"][value="${radio.dataset.minutes}"]`);
      if (minutes) minutes.checked = true;
    });
  });

  /* ------------------------------------------------------------ Countdowns (cooling-off) */
  const countdowns = $$("[data-countdown]");
  if (countdowns.length) {
    const update = () => {
      const now = Date.now();
      countdowns.forEach((el) => {
        const target = Number(el.dataset.countdown);
        if (now >= target) {
          window.location.reload();
        } else {
          el.textContent = clock(target - now);
        }
      });
    };
    update();
    setInterval(update, 1000);
  }

  /* ------------------------------------------------------------ Urge timer */
  const urge = $("#urge");
  if (urge) {
    const seconds = 60;
    const cycle = [["Breathe in", 4, "in"], ["Hold", 4, "hold"], ["Breathe out", 6, "out"]];
    const cycleLength = cycle.reduce((sum, c) => sum + c[1], 0);
    const orb = $("#orb");
    const text = $("#breath-text");
    const secs = $("#secs");
    const meter = $("#urge-meter");
    const breathe = $("#breathe");
    const reflect = $("#reflect");
    const startedAt = Date.now();
    let step = -1;
    let timer = null;

    const showReflect = () => {
      clearInterval(timer);
      breathe.classList.add("hidden");
      reflect.classList.remove("hidden");
      window.scrollTo({ top: 0, behavior: "smooth" });
    };

    const update = () => {
      const elapsed = (Date.now() - startedAt) / 1000;
      const left = Math.max(0, seconds - elapsed);
      secs.textContent = Math.ceil(left);
      meter.style.width = `${Math.min(100, (elapsed / seconds) * 100)}%`;

      let t = elapsed % cycleLength;
      let i = 0;
      while (t >= cycle[i][1]) { t -= cycle[i][1]; i++; }
      if (i !== step) {
        step = i;
        text.textContent = cycle[i][0];
        orb.className = `orb ${cycle[i][2]}`;
      }
      if (left <= 0) showReflect();
    };

    timer = setInterval(update, 250);
    update();
    const skip = $("#skip");
    if (skip) skip.addEventListener("click", showReflect);
  }

  /* ------------------------------------------------------------ Energy: enable save on choice */
  const energyForm = $("#energy-form");
  if (energyForm) {
    const save = $("button[type=submit]", energyForm);
    const sync = () => { save.disabled = !$('input[name="level"]:checked', energyForm); };
    $$('input[name="level"]', energyForm).forEach((r) => r.addEventListener("change", sync));
    sync();
  }

  /* ------------------------------------------------------------ Task editor: date row */
  const dateRow = $("#date-row");
  if (dateRow) {
    const sync = () => {
      const picked = $('input[name="when"]:checked');
      dateRow.classList.toggle("hidden", !picked || picked.value !== "date");
    };
    $$('input[name="when"]').forEach((r) => r.addEventListener("change", sync));
    sync();
  }
})();
