/*
 * Vervan web app — the second screen for the Android app.
 *
 * Everything here reads and writes the phone's own database through the local server; there is no
 * separate web-side state. A chat started in the browser is the same row the app opens, a note
 * edited here is the note on the phone, and generation runs on the device's model either way. The
 * browser is a view, not a copy.
 *
 * Structure: a tiny router over the sections declared in full.html, one controller object per
 * section, and a shared `api` helper. No framework and no build step — this file is served straight
 * out of the APK's assets, so it stays ES5-compatible and dependency-free apart from the vendored
 * Mermaid bundle that render.js loads on demand.
 */
(function () {
  "use strict";

  var R = window.VervanRender;
  var esc = R.escapeHtml;

  // A tiny inline icon set keeps the APK self-contained and makes the web surface feel like the
  // native app. Icons are SVG rather than text glyphs so they stay crisp across browsers and themes.
  var ICONS = {
    edit: '<path d="m4 16-.7 4.7L8 20l10.8-10.8a2.2 2.2 0 0 0-3.1-3.1z"/><path d="m14.5 7.5 3 3"/>',
    pin: '<path d="m15 4 5 5-2.2 2.2.2 5.3-3.1-3.1-3.4 3.4-1.3-1.3 3.4-3.4-3.1-3.1 5.3.2z"/>',
    pinOff: '<path d="m15 4 5 5-2.2 2.2.2 5.3-3.1-3.1-3.4 3.4-1.3-1.3 3.4-3.4-3.1-3.1 5.3.2z"/><path d="m4 4 16 16"/>',
    trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 14h10l1-14M9 7V4h6v3"/>',
    tool: '<path d="m14.7 6.3 3-3 3 3-3 3M13 8l3 3-7.8 7.8a2.1 2.1 0 0 1-3-3z"/><path d="m16 13 3 3"/>',
    file: '<path d="M6 3h8l4 4v14H6z"/><path d="M14 3v5h5"/>',
    audio: '<path d="M12 3v12M8 7v8a4 4 0 0 0 8 0V7M5 11v4a7 7 0 0 0 14 0v-4M12 22v-3"/>',
    thumbsUp: '<path d="M7 10v10H4V10zM7 19h8.7a2 2 0 0 0 1.9-1.4l1.8-5.5A1.6 1.6 0 0 0 18.9 10H14l.7-3.2A2.2 2.2 0 0 0 12.5 4L8 10"/>',
    thumbsDown: '<path d="M7 14V4H4v10zM7 5h8.7a2 2 0 0 1 1.9 1.4l1.8 5.5a1.6 1.6 0 0 1-1.5 2.1H14l.7 3.2a2.2 2.2 0 0 1-2.2 2.8L8 14"/>',
    more: '<circle cx="5" cy="12" r="1" fill="currentColor"/><circle cx="12" cy="12" r="1" fill="currentColor"/><circle cx="19" cy="12" r="1" fill="currentColor"/>',
    copy: '<rect x="8" y="8" width="11" height="11" rx="2"/><path d="M16 8V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h2"/>',
    close: '<path d="m6 6 12 12M18 6 6 18"/>'
  };
  function icon(name) { return '<svg class="ui-icon" viewBox="0 0 24 24" aria-hidden="true">' + (ICONS[name] || "") + "</svg>"; }

  // ---------------------------------------------------------------- token + transport

  // Settings' "Open web UI" hands the key over once as ?token=; it is stashed and stripped from
  // the address bar so it does not sit in history or get copied out of the URL bar by accident.
  var params = new URLSearchParams(location.search);
  var urlToken = params.get("token");
  if (urlToken) {
    localStorage.setItem("vervan_api_token", urlToken);
    history.replaceState({}, "", location.pathname);
  }
  function token() { return localStorage.getItem("vervan_api_token") || ""; }
  function authHeaders() {
    var t = token();
    return t ? { Authorization: "Bearer " + t } : {};
  }
  function jsonHeaders() {
    return Object.assign({ "Content-Type": "application/json" }, authHeaders());
  }

  function friendlyError(err) {
    if (err && err.name === "AbortError") return new Error("Vervan took too long to respond. Check that the local server and a model are ready.");
    if (err && /Failed to fetch|NetworkError|Load failed/i.test(err.message || "")) {
      return new Error("Could not reach Vervan on this device. Check that the local API server is running.");
    }
    return err instanceof Error ? err : new Error(String(err || "Something went wrong."));
  }

  function api(path, body, opts) {
    var options = Object.assign({}, opts || {});
    if (body !== undefined && body !== null) {
      options.method = options.method || "POST";
      options.headers = jsonHeaders();
      options.body = JSON.stringify(body);
    } else {
      options.headers = authHeaders();
    }
    // Generation has its own streaming path; every call through here is a small JSON round trip on
    // a local link, so a bounded timeout is better than a request that hangs the UI forever. A
    // document upload is the exception — extraction and embedding happen inline on the phone and
    // legitimately take minutes for a large PDF, so it passes its own budget.
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, options.timeoutMs || 20000);
    delete options.timeoutMs;
    options.signal = controller.signal;
    return fetch(path, options).then(function (res) {
      return res.text().then(function (raw) {
        clearTimeout(timer);
        var parsed = {};
        try { parsed = raw ? JSON.parse(raw) : {}; } catch (e) { parsed = {}; }
        if (res.status === 401) { setStatus("auth"); throw new Error("This server needs an API key."); }
        if (!res.ok) {
          throw new Error((parsed.error && parsed.error.message) || ("Request failed (HTTP " + res.status + ")"));
        }
        return parsed;
      });
    }, function (err) {
      clearTimeout(timer);
      throw friendlyError(err);
    });
  }

  // ---------------------------------------------------------------- small UI helpers

  var el = function (id) { return document.getElementById(id); };
  var toastTimer = null;
  function toast(message, isError) {
    var node = el("toast");
    node.textContent = message;
    node.className = "toast show" + (isError ? " error" : "");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { node.className = "toast"; }, 3200);
  }
  function fail(err) {
    var safe = friendlyError(err);
    if (/Could not reach Vervan|too long to respond/i.test(safe.message)) setStatus("err");
    toast(safe.message, true);
    return safe;
  }

  function showLoadError(targetId, title, err, retry) {
    var target = el(targetId);
    if (!target) return;
    var safe = friendlyError(err);
    target.innerHTML = '<div class="inline-state error-state"><h3>' + esc(title) + '</h3>' +
      '<p class="error-copy">' + esc(safe.message) + '</p>' +
      '<div class="state-actions"><button class="secondary" type="button" data-retry>Try again</button></div></div>';
    var button = target.querySelector("[data-retry]");
    if (button) button.addEventListener("click", function () {
      button.disabled = true;
      button.textContent = "Retrying…";
      Promise.resolve(retry()).catch(function (retryErr) { showLoadError(targetId, title, retryErr, retry); });
    });
  }

  function setStatus(state, label) {
    var LABELS = { ok: "Connected", busy: "Generating…", err: "Server issue", auth: "API key needed", idle: "Connecting…" };
    el("statusDot").className = "dot " + (state === "idle" ? "" : state);
    el("statusLabel").textContent = label || LABELS[state] || "";
  }

  function setGenerationState(visible, label, detail) {
    var node = el("generationState");
    if (!node) return;
    node.hidden = !visible;
    el("messages").setAttribute("aria-busy", visible ? "true" : "false");
    if (label) el("generationLabel").textContent = label;
    if (detail) el("generationDetail").textContent = detail;
  }

  function relativeTime(ms) {
    if (!ms) return "";
    var diff = Date.now() - ms;
    if (diff < 60000) return "just now";
    if (diff < 3600000) return Math.floor(diff / 60000) + "m ago";
    if (diff < 86400000) return Math.floor(diff / 3600000) + "h ago";
    if (diff < 604800000) return Math.floor(diff / 86400000) + "d ago";
    return new Date(ms).toLocaleDateString();
  }
  function bytes(n) {
    if (!n) return "";
    var units = ["B", "KB", "MB", "GB"], i = 0;
    while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
    return n.toFixed(i === 0 ? 0 : 1) + " " + units[i];
  }

  /** A modal built from a field spec. Every editor in the Library and Knowledge sections uses this
   * rather than hand-rolling a form, so they stay consistent and there is one place that handles
   * focus, Escape and the save/cancel pair. */
  function openModal(title, fields, onSave, extraButtons) {
    var modal = el("modal");
    var html = "<h2>" + esc(title) + "</h2>";
    var defaultMax = {
      title: 120, name: 100, description: 1000, content: 100000, body: 24000,
      text: 50000, system_instruction: 16000, instructions: 12000,
      label: 100, query: 200, reason: 200
    };
    fields.forEach(function (f) {
      html += '<label class="field"><span>' + esc(f.label) + "</span>";
      var maxLength = Number(f.maxLength || defaultMax[f.key] || 100000);
      var maxAttr = maxLength > 0 ? ' maxlength="' + maxLength + '"' : "";
      if (f.type === "textarea") {
        html += '<textarea id="mf_' + f.key + '"' + maxAttr + ' rows="' + (f.rows || 4) + '"' + '>' + esc(f.value || "") + "</textarea>";
      } else if (f.type === "select") {
        html += '<select id="mf_' + f.key + '">' + f.options.map(function (o) {
          return '<option value="' + esc(o.value) + '"' + (o.value === f.value ? " selected" : "") + ">" + esc(o.label) + "</option>";
        }).join("") + "</select>";
      } else if (f.type === "checkbox") {
        html += '<input type="checkbox" id="mf_' + f.key + '"' + (f.value ? " checked" : "") + ">";
      } else {
        html += '<input type="text" id="mf_' + f.key + '"' + maxAttr + ' value="' + esc(f.value || "") + '">';
      }
      html += "</label>";
    });
    html += '<div class="modal-actions">';
    (extraButtons || []).forEach(function (b, i) {
      html += '<button class="' + (b.className || "secondary") + '" type="button" id="mx_' + i + '">' + esc(b.label) + "</button>";
    });
    html += '<button class="secondary" type="button" id="mCancel">Cancel</button>' +
            '<button class="primary" type="button" id="mSave">Save</button></div>';
    modal.innerHTML = html;
    el("modalBack").className = "modal-back show";

    (extraButtons || []).forEach(function (b, i) {
      el("mx_" + i).addEventListener("click", function () { b.onClick(closeModal); });
    });
    el("mCancel").addEventListener("click", closeModal);
    el("mSave").addEventListener("click", function () {
      var values = {};
      fields.forEach(function (f) {
        var input = el("mf_" + f.key);
        values[f.key] = f.type === "checkbox" ? input.checked : input.value;
      });
      // Saving is the slow part of most editors (it writes through to the phone's database), so
      // the button shows it is working rather than the dialog appearing to hang.
      withBusy(el("mSave"), Promise.resolve(onSave(values))).then(closeModal, fail);
    });
    upgradeSelectsIn(modal);
    var first = modal.querySelector("input, textarea");
    if (first) first.focus();
  }
  function closeModal() { el("modalBack").className = "modal-back"; }
  el("modalBack").addEventListener("click", function (e) { if (e.target === el("modalBack")) closeModal(); });
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeModal(); });

  /**
   * In-page confirmation, replacing window.confirm().
   *
   * Not cosmetic: several mobile browsers suppress native dialogs from a page that isn't the
   * active tab or that has triggered too many, and a suppressed confirm() returns false silently —
   * which is why deleting or pinning a chat could look like it simply did nothing. This also shows
   * the destructive action's own progress instead of freezing the page.
   */
  function confirmThen(message, action, options) {
    var opts = options || {};
    var modal = el("modal");
    modal.className = "modal narrow";
    modal.innerHTML =
      "<h2>" + esc(opts.title || "Are you sure?") + "</h2>" +
      '<p class="confirm-text">' + esc(message) + "</p>" +
      '<div class="modal-actions">' +
        '<button class="secondary" type="button" id="cCancel">Cancel</button>' +
        '<button class="' + (opts.danger === false ? "primary" : "danger") + '" type="button" id="cOk">' +
          esc(opts.confirmLabel || "Delete") + "</button>" +
      "</div>";
    el("modalBack").className = "modal-back show";
    el("cCancel").addEventListener("click", closeModal);
    el("cOk").addEventListener("click", function () {
      var button = el("cOk");
      button.classList.add("busy");
      Promise.resolve(action()).then(closeModal, function (err) {
        button.classList.remove("busy");
        fail(err);
      });
    });
    el("cOk").focus();
  }

  // ---------------------------------------------------------------- custom select

  /**
   * Upgrades a native `<select>` into a themed dropdown.
   *
   * The OS draws a native select's popup, so it ignores this page's theme entirely and looks
   * foreign next to everything else — especially on Android. The `<select>` itself stays in the
   * DOM (hidden) and remains the source of truth: `.value` reads, assignments and `change` events
   * all keep working, so nothing that talks to these controls had to change. Re-running upgrade()
   * after the options are replaced re-syncs the visible list.
   */
  var openSelect = null;
  function upgradeSelect(select) {
    if (!select) return;
    var wrap = select.parentNode;
    if (!wrap || !wrap.classList.contains("xselect")) {
      wrap = document.createElement("div");
      wrap.className = "xselect" + (select.dataset.pill === "1" ? " pill" : "");
      select.parentNode.insertBefore(wrap, select);
      wrap.appendChild(select);
      var button = document.createElement("button");
      button.type = "button";
      button.className = "xselect-btn";
      button.innerHTML = '<span class="val"></span><span class="caret">▼</span>';
      var list = document.createElement("div");
      list.className = "xselect-list";
      list.hidden = true;
      wrap.appendChild(button);
      wrap.appendChild(list);

      button.addEventListener("click", function (e) {
        e.stopPropagation();
        if (select.disabled) return;
        var isOpen = !list.hidden;
        closeAllSelects();
        if (isOpen) return;
        // Open upward when the control sits low in the viewport, so the list isn't cut off.
        wrap.classList.toggle("drop-up", button.getBoundingClientRect().bottom > window.innerHeight - 300);
        list.hidden = false;
        wrap.classList.add("open");
        openSelect = wrap;
        var active = list.querySelector('[aria-selected="true"]');
        if (active) active.scrollIntoView({ block: "nearest" });
      });
      list.addEventListener("click", function (e) {
        var option = e.target.closest && e.target.closest(".xselect-opt");
        if (!option) return;
        e.stopPropagation();
        select.value = option.getAttribute("data-value");
        closeAllSelects();
        syncSelect(select);
        // Dispatched so the existing change listeners fire exactly as for a native pick.
        select.dispatchEvent(new Event("change", { bubbles: true }));
      });
    }
    syncSelect(select);
  }

  function syncSelect(select) {
    var wrap = select.parentNode;
    if (!wrap || !wrap.classList.contains("xselect")) return;
    var button = wrap.querySelector(".xselect-btn");
    var list = wrap.querySelector(".xselect-list");
    var options = Array.prototype.slice.call(select.options);
    button.querySelector(".val").textContent =
      (select.selectedIndex >= 0 && options[select.selectedIndex]) ? options[select.selectedIndex].text : "";
    button.disabled = select.disabled || !options.length;
    list.innerHTML = options.map(function (option) {
      return '<button type="button" class="xselect-opt" data-value="' + esc(option.value) + '"' +
        ' aria-selected="' + (option.value === select.value ? "true" : "false") + '">' +
        "<span>" + esc(option.text) + '</span><span class="tick">✓</span></button>';
    }).join("");
  }

  function closeAllSelects() {
    Array.prototype.forEach.call(document.querySelectorAll(".xselect"), function (wrap) {
      wrap.classList.remove("open");
      var list = wrap.querySelector(".xselect-list");
      if (list) list.hidden = true;
    });
    openSelect = null;
  }
  document.addEventListener("click", closeAllSelects);
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeAllSelects(); });

  /** Upgrades every select inside [root] that hasn't been upgraded yet, and re-syncs the ones that
   * already were. Safe to call repeatedly — it is how the visible list catches up after options or
   * a value are replaced programmatically (loading models, hydrating a chat's settings). */
  function upgradeSelectsIn(root) {
    Array.prototype.forEach.call((root || document).querySelectorAll("select"), upgradeSelect);
  }

  // ---------------------------------------------------------------- loading helpers

  /** Marks a button busy for the life of a promise, so the user sees the action is running and
   * can't fire it twice. */
  function withBusy(button, promise) {
    if (!button) return promise;
    button.classList.add("busy");
    return promise.then(function (value) {
      button.classList.remove("busy");
      return value;
    }, function (err) {
      button.classList.remove("busy");
      throw err;
    });
  }

  function skeletonRows(count) {
    var out = "";
    for (var i = 0; i < (count || 5); i++) {
      out += '<div class="skeleton-row"><div class="skeleton title"></div><div class="skeleton short"></div></div>';
    }
    return out;
  }
  function skeletonCards(count) {
    var out = "";
    for (var i = 0; i < (count || 3); i++) {
      out += '<div class="skeleton-card"><div class="skeleton title"></div><div class="skeleton line"></div><div class="skeleton short"></div></div>';
    }
    return out;
  }
  function loadingBlock(label) {
    return '<div class="loading-block"><span class="spinner lg"></span>' + esc(label || "Loading…") + "</div>";
  }

  /** The indeterminate bar under the header, for background refreshes that must not blank the
   * content already on screen. Reference-counted: concurrent loads don't cancel each other's bar. */
  var pendingLoads = 0;
  function beginLoad() {
    pendingLoads++;
    el("topProgress").hidden = false;
  }
  function endLoad() {
    pendingLoads = Math.max(0, pendingLoads - 1);
    if (!pendingLoads) el("topProgress").hidden = true;
  }
  /** Wraps a promise in the top progress bar. */
  function tracked(promise) {
    beginLoad();
    return promise.then(function (value) { endLoad(); return value; }, function (err) { endLoad(); throw err; });
  }

  // ---------------------------------------------------------------- routing

  var SECTIONS = {
    home: { title: "Home", load: function () { Home.load(); } },
    chats: { title: "Chats", load: function () { Chat.load(); } },
    library: { title: "Library", load: function () { Library.load(); } },
    knowledge: { title: "Knowledge", load: function () { Knowledge.load(); } },
    tools: { title: "Tools", load: function () { Tools.load(); } },
    models: { title: "Models", load: function () { Models.load(); } },
    recycle: { title: "Recycle bin", load: function () { Recycle.load(); } }
  };
  var current = "home";

  function go(name) {
    if (!SECTIONS[name]) name = "home";
    current = name;
    Object.keys(SECTIONS).forEach(function (key) {
      el("section-" + key).classList.toggle("active", key === name);
    });
    Array.prototype.forEach.call(document.querySelectorAll(".rail-btn"), function (b) {
      b.classList.toggle("active", b.getAttribute("data-section") === name);
    });
    el("pageTitle").textContent = SECTIONS[name].title;
    closeLists();
    if (location.hash !== "#" + name) history.replaceState({}, "", "#" + name);
    SECTIONS[name].load();
  }
  Array.prototype.forEach.call(document.querySelectorAll(".rail-btn"), function (b) {
    b.addEventListener("click", function () { go(b.getAttribute("data-section")); });
  });
  window.addEventListener("hashchange", function () { go((location.hash || "#home").slice(1)); });

  // On a phone-width viewport the list column is a slide-over, so it needs an explicit toggle and
  // a scrim; on a wide one both are inert because the column is always visible.
  function listColFor(section) {
    return { chats: "chatListCol", library: "libListCol", knowledge: "kbListCol", tools: "toolListCol" }[section];
  }
  function closeLists() {
    Array.prototype.forEach.call(document.querySelectorAll(".list-col"), function (c) { c.classList.remove("open"); });
    el("scrim").className = "scrim";
    el("listToggle").setAttribute("aria-expanded", "false");
    el("optsCol").classList.add("hidden");
    el("optsToggle").setAttribute("aria-expanded", "false");
  }
  el("listToggle").addEventListener("click", function () {
    var id = listColFor(current);
    if (!id) return;
    var open = el(id).classList.toggle("open");
    el("scrim").className = open ? "scrim show" : "scrim";
    el("listToggle").setAttribute("aria-expanded", String(open));
  });
  el("scrim").addEventListener("click", closeLists);
  el("keyBtn").addEventListener("click", function () {
    openModal("API key", [{ key: "token", label: "Bearer token (leave blank if the server does not require one)", value: token() }], function (v) {
      localStorage.setItem("vervan_api_token", v.token.trim());
      toast("Key saved");
      go(current);
    });
  });

  // ================================================================ HOME

  var Home = {
    load: function () {
      // Skeletons rather than a blank pane: the home screen's shape is known before its numbers
      // are, so showing that shape immediately reads as "arriving" instead of "broken".
      if (!el("homeStats").children.length) {
        el("homeStats").innerHTML = skeletonCards(6);
        el("homeModels").innerHTML = skeletonRows(2);
        el("homeChats").innerHTML = skeletonRows(3);
        el("homeNotes").innerHTML = skeletonRows(2);
      }
      tracked(api("/api/overview")).then(function (data) {
        setStatus("ok");
        var counts = data.counts || {};
        el("homeStats").innerHTML = [
          ["Chats", counts.chats], ["Notes", counts.notes], ["Memories", counts.memories],
          ["Knowledge bases", counts.knowledge_bases], ["Documents", counts.documents], ["Projects", counts.projects]
        ].map(function (p) {
          return '<div class="stat"><b>' + (p[1] || 0) + "</b><span>" + p[0] + "</span></div>";
        }).join("");

        el("homeModels").innerHTML = Models.summaryHtml(data.models);
        el("homeChats").innerHTML = (data.recent_chats || []).length
          ? (data.recent_chats).map(function (c) {
              return '<div class="row" data-chat="' + esc(c.id) + '"><div class="row-title">' +
                (c.pinned ? icon("pin") + " " : "") + esc(c.title) + '</div><div class="row-sub">' + relativeTime(c.updated_at) + "</div></div>";
            }).join("")
          : '<div class="empty-note">No chats yet.</div>';
        Array.prototype.forEach.call(el("homeChats").querySelectorAll("[data-chat]"), function (row) {
          row.addEventListener("click", function () {
            Chat.pendingOpen = row.getAttribute("data-chat");
            go("chats");
          });
        });
        el("homeNotes").innerHTML = (data.recent_notes || []).length
          ? (data.recent_notes).map(function (n) {
              return '<div class="row"><div class="row-title">' + esc(n.title) +
                '</div><div class="row-sub">' + relativeTime(n.updated_at) + "</div></div>";
            }).join("")
          : '<div class="empty-note">No notes yet.</div>';
      }).catch(function (err) {
        setStatus(/API key/.test(err.message) ? "auth" : "err");
        showLoadError("homeStats", "Home is unavailable", err, Home.load);
        fail(err);
      });
    }
  };

  // Home → "API & OpenAPI spec". Absolute URLs are built from location.origin rather than
  // hardcoded, because the useful value is whatever host the user actually reached this page on
  // (a LAN IP, usually) — a copied "/openapi.json" or "127.0.0.1" is useless on their laptop.
  (function wireApiSpecCard() {
    var link = el("openApiLink");
    var copyBtn = el("copySpecUrlBtn");
    var curl = el("specCurl");
    if (!link) return;
    var specUrl = location.origin + "/openapi.json";
    link.href = specUrl;
    if (curl) {
      curl.textContent =
        "curl " + location.origin + "/v1/chat/completions \\\n" +
        '  -H "Content-Type: application/json" \\\n' +
        '  -d \'{"messages":[{"role":"user","content":"Hello"}],"stream":true}\'';
    }
    if (copyBtn) {
      copyBtn.addEventListener("click", function () {
        if (!navigator.clipboard) { toast("Clipboard isn't available in this browser", true); return; }
        navigator.clipboard.writeText(specUrl)
          .then(function () { toast("Spec URL copied"); })
          .catch(function () { toast("Couldn't copy the spec URL", true); });
      });
    }
  })();

  var searchTimer = null;
  el("globalSearch").addEventListener("input", function () {
    clearTimeout(searchTimer);
    var q = el("globalSearch").value.trim();
    if (!q) { el("searchResults").innerHTML = ""; return; }
    searchTimer = setTimeout(function () {
      api("/api/search?q=" + encodeURIComponent(q)).then(function (data) {
        var results = data.results || [];
        el("searchResults").innerHTML = results.length
          ? results.map(function (r) {
              return '<div class="row" data-type="' + esc(r.type) + '" data-id="' + esc(r.id) + '">' +
                '<div class="row-title">' + esc(r.title) + "</div>" +
                '<div class="row-sub">' + esc(r.type.replace(/_/g, " ")) + (r.snippet ? " · " + esc(r.snippet) : "") + "</div></div>";
            }).join("")
          : '<div class="empty-note">Nothing matched “' + esc(q) + "”.</div>";
        Array.prototype.forEach.call(el("searchResults").querySelectorAll(".row"), function (row) {
          row.addEventListener("click", function () {
            var type = row.getAttribute("data-type"), id = row.getAttribute("data-id");
            if (type === "chat") { Chat.pendingOpen = id; go("chats"); }
            else if (type === "knowledge_base" || type === "document") { Knowledge.pendingOpen = id; go("knowledge"); }
            else { Library.pendingKind = type === "memory" ? "memories" : type + "s"; go("library"); }
          });
        });
      }).catch(fail);
    }, 220);
  });

  // ================================================================ CHAT

  var Chat = {
    chats: [], models: [], kbs: [], tools: [], personas: [], projects: [], folders: [], messages: [],
    activeId: null, config: null, pendingOpen: null, attachments: [], abort: null, loaded: false,
    // Set while loading a chat's stored settings into the panel, so the change handlers those
    // assignments fire don't immediately POST the values straight back as if the user typed them.
    hydrating: false,
    // Which sliders the user has actually moved in this chat — see saveConfig for why "not yet
    // touched" has to stay distinct from "sitting at the default position".
    touched: {},

    load: function () {
      var first = !this.loaded;
      this.loaded = true;
      var jobs = [this.refreshList()];
      if (first) jobs.push(this.loadModels(), this.loadKbs(), this.loadTools(), this.loadOrganisers());
      Promise.all(jobs).then(function () {
        if (Chat.pendingOpen) { var id = Chat.pendingOpen; Chat.pendingOpen = null; Chat.open(id); }
        else if (!Chat.activeId) Chat.renderEmpty();
      }).catch(function (err) {
        showLoadError("chatList", "Chats are unavailable", err, Chat.load);
        fail(err);
      });
    },

    /** Personas, projects and folders back the chat-settings selects. Fetched once per session —
     * they change rarely, and the Library section refreshes them when it edits one. */
    loadOrganisers: function () {
      return Promise.all([
        api("/api/personas").then(function (d) { Chat.personas = d.personas || []; }),
        api("/api/projects").then(function (d) { Chat.projects = d.projects || []; }),
        api("/api/folders").then(function (d) { Chat.folders = d.folders || []; })
      ]).then(function () {
        function fill(id, items, blank, label) {
          el(id).innerHTML = '<option value="">' + blank + "</option>" +
            items.map(function (i) { return '<option value="' + esc(i.id) + '">' + esc(label(i)) + "</option>"; }).join("");
        }
        fill("personaSelect", Chat.personas, "No persona", function (p) { return p.name; });
        fill("projectSelect", Chat.projects, "No project", function (p) { return p.name; });
        fill("folderSelect", Chat.folders, "No folder", function (f) { return f.name; });
        upgradeSelectsIn(el("optsCol"));
      });
    },

    refreshList: function () {
      if (!Chat.chats.length) el("chatList").innerHTML = skeletonRows(6);
      return tracked(api("/api/chats?limit=200")).then(function (data) {
        Chat.chats = data.data || [];
        Chat.renderList();
      });
    },

    renderList: function () {
      var q = el("chatSearch").value.trim().toLowerCase();
      var shown = Chat.chats.filter(function (c) { return !q || c.title.toLowerCase().indexOf(q) >= 0; });
      el("chatList").innerHTML = shown.length ? shown.map(function (c) {
        return '<div class="row' + (c.id === Chat.activeId ? " active" : "") + '" data-id="' + esc(c.id) + '">' +
          '<div class="row-title">' + (c.pinned ? icon("pin") + " " : "") + esc(c.title) + "</div>" +
          '<div class="row-sub">' + c.message_count + " message" + (c.message_count === 1 ? "" : "s") + " · " + relativeTime(c.updated_at) + "</div>" +
          '<div class="row-actions">' +
            '<button class="icon-btn" data-act="rename" title="Rename" aria-label="Rename">' + icon("edit") + "</button>" +
            '<button class="icon-btn" data-act="pin" title="' + (c.pinned ? "Unpin" : "Pin") + '" aria-label="' + (c.pinned ? "Unpin" : "Pin") + '">' + icon(c.pinned ? "pin" : "pinOff") + "</button>" +
            '<button class="icon-btn destructive" data-act="delete" title="Delete" aria-label="Delete">' + icon("trash") + "</button>" +
          "</div></div>";
      }).join("") : '<div class="empty-note">No chats yet.</div>';

      Array.prototype.forEach.call(el("chatList").querySelectorAll(".row"), function (row) {
        var id = row.getAttribute("data-id");
        var chat = Chat.chats.find(function (c) { return c.id === id; });
        row.addEventListener("click", function (e) {
          var act = e.target.getAttribute && e.target.getAttribute("data-act");
          if (!act) { Chat.open(id); closeLists(); return; }
          e.stopPropagation();
          if (act === "rename") {
            openModal("Rename chat", [{ key: "title", label: "Title", value: chat.title }], function (v) {
              return api("/api/chats/update", { id: id, title: v.title }).then(function () { return Chat.refreshList(); });
            });
          } else if (act === "pin") {
            api("/api/chats/update", { id: id, pinned: !chat.pinned }).then(function () { return Chat.refreshList(); }).catch(fail);
          } else {
            confirmThen("Move “" + chat.title + "” to the recycle bin?", function () {
              return api("/api/chats/delete", { id: id }).then(function () {
                if (Chat.activeId === id) { Chat.activeId = null; Chat.renderEmpty(); }
                return Chat.refreshList();
              });
            });
          }
        });
      });
    },

    loadModels: function () {
      return api("/v1/models").then(function (data) {
        Chat.models = (data.data || []).filter(function (m) { return m.role !== "embedding"; });
        var select = el("modelSelect");
        select.innerHTML = Chat.models.map(function (m) {
          return '<option value="' + esc(m.id) + '"' + (m.active ? " selected" : "") + ">" + esc(m.id) + "</option>";
        }).join("") || '<option value="">No models installed</option>';
        upgradeSelect(el("modelSelect"));
        Chat.syncCapabilities();
      });
    },

    /**
     * Reflects the selected model's capabilities in the composer.
     *
     * Nothing here is disabled on the strength of a capability flag alone. Those flags are often
     * simply unset on an imported model — `supports_thinking` in particular has no fallback and
     * reads false for most models — so gating controls on them made the thinking selector and the
     * attach button permanently dead. Capabilities annotate; the server and the model decide.
     * Documents and OCR are listed regardless, because neither needs vision or audio: a document
     * is extracted to text and OCR runs on the device, not in the model.
     */
    syncCapabilities: function () {
      var model = Chat.models.find(function (m) { return m.id === el("modelSelect").value; }) || {};
      var badges = [];
      if (model.supports_vision) badges.push("vision");
      if (model.supports_audio) badges.push("audio");
      if (model.supports_tools) badges.push("tools");
      if (model.supports_thinking) badges.push("thinking");
      el("capChip").textContent = badges.join("  ") || "text";
      Chat.model = model;
    },

    loadKbs: function () {
      return api("/api/knowledge-bases").then(function (data) {
        Chat.kbs = data.data || [];
        el("kbCheckList").innerHTML = Chat.kbs.length ? Chat.kbs.map(function (kb) {
          return '<label><input type="checkbox" value="' + esc(kb.id) + '"> ' + esc(kb.name) +
            ' <span class="row-sub">(' + kb.document_count + ")</span></label>";
        }).join("") : '<div class="empty-note">No knowledge bases yet.</div>';
      });
    },

    loadTools: function () {
      return api("/api/tools").then(function (data) {
        Chat.tools = data.data || [];
        el("toolsToggle").checked = !!data.app_tools_enabled;
        el("toolCheckList").innerHTML = Chat.tools.length ? Chat.tools.map(function (t) {
          return '<label title="' + esc(t.description) + '"><input type="checkbox" value="' + esc(t.name) + '"' +
            (t.enabled ? " checked" : "") + "> " + esc(t.name) +
            (t.risk !== "READ_ONLY" ? ' <span class="chip warn">write</span>' : "") + "</label>";
        }).join("") : '<div class="empty-note">No tools available.</div>';
        if (!data.app_tools_enabled) {
          el("toolCheckList").insertAdjacentHTML("afterbegin",
            '<div class="empty-note">Tools are off for API clients. Turn them on in the app under Settings → Local API server.</div>');
        }
      });
    },

    selectedKbIds: function () {
      if (!el("ragToggle").checked) return [];
      return Array.prototype.slice.call(el("kbCheckList").querySelectorAll("input:checked"))
        .map(function (i) { return i.value; });
    },
    selectedToolNames: function () {
      return Array.prototype.slice.call(el("toolCheckList").querySelectorAll("input:checked"))
        .map(function (i) { return i.value; });
    },

    /** Only tools whose state differs from their global Settings value are recorded as overrides —
     * a chat that agrees with the defaults stores nothing, so later changing a tool globally still
     * affects it, exactly as "inherit" should behave. */
    toolOverrides: function () {
      var overrides = {};
      Array.prototype.forEach.call(el("toolCheckList").querySelectorAll("input"), function (input) {
        var tool = Chat.tools.find(function (t) { return t.name === input.value; });
        var globalState = !!(tool && tool.enabled);
        if (input.checked !== globalState) overrides[input.value] = input.checked;
      });
      return overrides;
    },

    renderEmpty: function () {
      el("thread").innerHTML =
        '<div class="chat-empty"><div style="width:56px;margin:0 auto">' + (window.VERVAN_ICON_SVG || "") + "</div>" +
        "<h2>Your phone's model, in this browser.</h2>" +
        "<p>Everything you send here is stored on the device, in the same chats the app shows.</p>" +
        '<div class="prompt-grid">' +
        ['Summarize what I worked on recently', 'Explain a concept simply', 'Draft a reply to this message'].map(function (p) {
          return '<button class="prompt-chip" type="button" data-prompt="' + esc(p) + '">' + esc(p) + "</button>";
        }).join("") + "</div></div>";
      Array.prototype.forEach.call(el("thread").querySelectorAll("[data-prompt]"), function (b) {
        b.addEventListener("click", function () {
          el("input").value = b.getAttribute("data-prompt");
          el("input").focus();
          autoGrow();
        });
      });
    },

    open: function (id) {
      Chat.activeId = id;
      Chat.renderList();
      el("thread").innerHTML = loadingBlock("Opening chat…");
      return Promise.all([
        api("/api/chat?id=" + encodeURIComponent(id)),
        api("/api/messages?chat_id=" + encodeURIComponent(id) + "&limit=300")
      ]).then(function (results) {
        Chat.applyConfig(results[0]);
        var messages = results[1].data || [];
        Chat.messages = messages;
        el("thread").innerHTML = "";
        if (!messages.length) { Chat.renderEmpty(); return; }
        messages.forEach(function (m) { Chat.appendStored(m); });
        Chat.scrollToEnd();
      }).catch(fail);
    },

    /** The question an assistant answer belongs to — what "Retry" re-asks. */
    previousUserMessage: function (assistantMessage) {
      var index = Chat.messages.map(function (m) { return m.id; }).indexOf(assistantMessage.id);
      for (var i = index - 1; i >= 0; i--) {
        if (Chat.messages[i].role === "user") return Chat.messages[i];
      }
      return null;
    },

    /**
     * Drops [userMessage] and everything after it, then asks again with [text]. Backs both "edit a
     * question" and "retry an answer", which are the same operation with a different source of text.
     *
     * Deletion runs newest-first: `handleDeleteMessage` walks the chat's `activeLeafId` back to the
     * deleted row's parent, so removing from the tail leaves the pointer valid at every step
     * instead of dangling mid-way through.
     */
    replaceFrom: function (userMessage, text) {
      var index = Chat.messages.map(function (m) { return m.id; }).indexOf(userMessage.id);
      if (index < 0) return Promise.reject(new Error("That message is no longer part of this chat."));
      if (userMessage.has_image || userMessage.has_audio) {
        toast("The original attachment won't be resent — attach it again if it matters.");
      }
      var doomed = Chat.messages.slice(index).reverse();
      var chain = Promise.resolve();
      doomed.forEach(function (m) {
        chain = chain.then(function () {
          return api("/api/messages/delete", { id: m.id, chat_id: Chat.activeId });
        });
      });
      return chain
        .then(function () { return Chat.open(Chat.activeId); })
        .then(function () {
          el("input").value = text;
          autoGrow();
          Chat.send();
        });
    },

    /** Loads a chat's stored configuration into the settings panel. This is what makes the panel a
     * view of the chat rather than a set of transient controls: reopening a conversation restores
     * the model, persona, thinking level, knowledge bases and sampling it was configured with,
     * exactly as it does in the app. */
    applyConfig: function (config) {
      Chat.config = config;
      Chat.hydrating = true;
      Chat.touched = {};
      try {
        el("optsBody").style.display = "";
        el("optsNoChat").style.display = "none";
        el("chatTitle").textContent = config.title || "New chat";
        el("incognitoChip").hidden = !config.is_temporary;
        // Only restore a draft into an empty composer — never overwrite something being typed.
        if (config.draft && !el("input").value) { el("input").value = config.draft; autoGrow(); }
        if (config.model_id) {
          var model = Chat.models.find(function (m) { return m.model_id === config.model_id || m.id === config.model_id; });
          if (model) el("modelSelect").value = model.id;
        }
        el("thinkingSelect").value = config.thinking_mode || "";
        el("personaSelect").value = config.persona_id || "";
        el("projectSelect").value = config.project_id || "";
        el("folderSelect").value = config.folder_id || "";
        el("profileSelect").value = config.profile || "BALANCED";
        el("toolsToggle").checked = !!config.tools_enabled;
        el("archivedToggle").checked = !!config.archived;
        el("topK").value = config.top_k == null ? "" : config.top_k;
        // Three-state, same as the app: an override wins, otherwise the tool's global setting.
        var overrides = config.tool_overrides || {};
        Array.prototype.forEach.call(el("toolCheckList").querySelectorAll("input"), function (input) {
          var tool = Chat.tools.find(function (t) { return t.name === input.value; });
          input.checked = Object.prototype.hasOwnProperty.call(overrides, input.value)
            ? !!overrides[input.value]
            : !!(tool && tool.enabled);
        });
        el("ragToggle").checked = (config.knowledge_base_ids || []).length > 0;
        var selected = config.knowledge_base_ids || [];
        Array.prototype.forEach.call(el("kbCheckList").querySelectorAll("input"), function (input) {
          input.checked = selected.indexOf(input.value) >= 0;
        });
        if (config.temperature != null) {
          el("temperature").value = config.temperature;
          el("temperatureValue").textContent = Number(config.temperature).toFixed(2);
        }
        if (config.top_p != null) {
          el("topP").value = config.top_p;
          el("topPValue").textContent = Number(config.top_p).toFixed(2);
        }
        Chat.syncCapabilities();
        el("ragHint").textContent = el("ragToggle").checked ? "Answers will cite your documents" : "";
        // The assignments above set the underlying <select> values directly, which the themed
        // dropdowns can't observe — re-sync so they show what the chat is actually configured with.
        upgradeSelectsIn(el("section-chats"));
      } finally {
        Chat.hydrating = false;
      }
    },

    /** Writes the settings panel back to the chat row. Debounced because a range slider fires a
     * change per pixel, and coalesced into one PATCH-shaped call so a half-applied set of settings
     * is never persisted. */
    saveConfig: function () {
      if (Chat.hydrating || !Chat.activeId) return;
      clearTimeout(Chat.saveTimer);
      el("optsSaved").textContent = "Saving…";
      Chat.saveTimer = setTimeout(function () {
        var modelName = el("modelSelect").value;
        var model = Chat.models.find(function (m) { return m.id === modelName; });
        var payload = {
          id: Chat.activeId,
          thinking_mode: el("thinkingSelect").value,
          persona_id: el("personaSelect").value,
          project_id: el("projectSelect").value,
          folder_id: el("folderSelect").value,
          profile: el("profileSelect").value,
          tools_enabled: el("toolsToggle").checked,
          archived: el("archivedToggle").checked,
          knowledge_base_ids: Chat.selectedKbIds(),
          tool_overrides: Chat.toolOverrides(),
          top_k: parseInt(el("topK").value, 10) > 0 ? parseInt(el("topK").value, 10) : null
        };

        // Omitted rather than sent as "": clearing a chat's model because the select briefly had a
        // value this client couldn't resolve would be worse than leaving it alone. An empty select
        // (no models installed) is a real "no model", so that case still clears.
        if (!modelName) payload.model_id = "";
        else if (model) payload.model_id = model.model_id;

        // Temperature and top-p are null on a chat that inherits the model/global value. The
        // sliders can't represent "inherit", so they are only written once the user has actually
        // moved one — otherwise merely opening a chat and toggling something unrelated would pin
        // both to the slider's resting position and silently end the inheritance.
        if (Chat.touched.temperature || (Chat.config && Chat.config.temperature != null)) {
          payload.temperature = parseFloat(el("temperature").value);
        }
        if (Chat.touched.topP || (Chat.config && Chat.config.top_p != null)) {
          payload.top_p = parseFloat(el("topP").value);
        }

        api("/api/chats/update", payload).then(function (config) {
          Chat.config = config;
          el("optsSaved").textContent = "Saved";
          setTimeout(function () { el("optsSaved").textContent = ""; }, 1600);
          return Chat.refreshList();
        }).catch(function (err) {
          el("optsSaved").textContent = "";
          fail(err);
        });
      }, 400);
    },

    /** Renders one persisted message, including the parts a plain-text view would drop: the
     * image/audio it carried, a tool call and its result, and RAG provenance. */
    appendStored: function (m) {
      var wrap = document.createElement("div");
      wrap.className = "msg " + (m.role === "user" ? "user" : "assistant");
      var body = document.createElement("div");
      body.className = "msg-body";

      if (m.has_image) {
        body.appendChild(Chat.mediaNode(m.chat_id || Chat.activeId, m.id, "image"));
      }
      if (m.has_audio) {
        body.appendChild(Chat.mediaNode(m.chat_id || Chat.activeId, m.id, "audio"));
      }
      if (m.tool_call) {
        var call = document.createElement("div");
        call.className = "msg-tool";
        var parsedCall = typeof m.tool_call === "string" ? tryParse(m.tool_call) : m.tool_call;
        call.innerHTML = icon("tool") + "<b>" + esc((parsedCall && parsedCall.tool) || "tool") + "</b> requested";
        body.appendChild(call);
      }
      if (m.tool_result) {
        var parsedResult = typeof m.tool_result === "string" ? tryParse(m.tool_result) : m.tool_result;
        var result = document.createElement("div");
        result.className = "msg-tool" + (parsedResult && parsedResult.success === false ? " failed" : "");
        result.innerHTML = icon("tool") + "<b>" + esc((parsedResult && parsedResult.tool) || "tool") + "</b> " +
          esc((parsedResult && parsedResult.summary) || "");
        body.appendChild(result);
      }

      var text = document.createElement("div");
      var split = splitThinking(m.content || "");
      if (split.thinking) body.appendChild(thinkingNode(split.thinking));
      R.renderMarkdown(text, split.answer);
      body.appendChild(text);
      wrap.appendChild(body);

      if (m.sources) {
        var sources = typeof m.sources === "string" ? tryParse(m.sources) : m.sources;
        if (sources && sources.length) {
          var list = document.createElement("ul");
          list.className = "msg-sources";
          sources.forEach(function (s) {
            var li = document.createElement("li");
            li.innerHTML = icon("file") + esc(s.documentName || s.document_name || "source");
            list.appendChild(li);
          });
          wrap.appendChild(list);
        }
      }

      var meta = document.createElement("div");
      meta.className = "msg-meta";
      var bits = [];
      if (m.model_name) bits.push(m.model_name);
      if (m.generation_ms) bits.push((m.generation_ms / 1000).toFixed(1) + "s");
      if (m.token_count) bits.push(m.token_count + " tok");
      if (m.state && m.state !== "COMPLETE") bits.push(m.state.toLowerCase());
      // Reactions and Copy stay inline because they're one-tap and frequent; everything else lives
      // behind "⋯", the same shape the app's own message sheet has, so the row stays readable
      // instead of turning into seven competing buttons.
      meta.innerHTML = "<span>" + esc(bits.join(" · ")) + "</span>" +
        '<span class="msg-actions">' +
          (m.role === "assistant"
            ? '<button class="icon-btn react-btn' + (m.reaction === "up" ? " on" : "") + '" data-act="up" title="Good answer" aria-label="Good answer">' + icon("thumbsUp") + "</button>" +
              '<button class="icon-btn react-btn' + (m.reaction === "down" ? " on" : "") + '" data-act="down" title="Bad answer" aria-label="Bad answer">' + icon("thumbsDown") + "</button>"
            : "") +
          '<button class="icon-btn" data-act="copy" title="Copy" aria-label="Copy">' + icon("copy") + "</button>" +
          '<span class="menu-wrap">' +
            '<button class="icon-btn" data-act="more" title="More" aria-label="More" aria-haspopup="true">' + icon("more") + "</button>" +
            '<div class="menu menu-up menu-msg" hidden>' +
              (m.role === "user"
                ? '<button type="button" data-act="edit">Edit and resend</button>'
                : '<button type="button" data-act="retry">Regenerate answer</button>') +
              '<button type="button" data-act="fork">Branch from here</button>' +
              (m.role === "assistant" ? '<button type="button" data-act="save">Save to library</button>' : "") +
              '<button type="button" data-act="copy">Copy text</button>' +
              "<hr>" +
              '<button type="button" data-act="delete" class="destructive">Delete message</button>' +
            "</div>" +
          "</span>" +
        "</span>";
      meta.addEventListener("click", function (e) {
        var act = e.target.getAttribute && e.target.getAttribute("data-act");
        if (!act) return;
        var menu = meta.querySelector(".menu-msg");
        if (act === "more") {
          e.stopPropagation();
          // Close any other open message menu first, so only one is ever up.
          Array.prototype.forEach.call(document.querySelectorAll(".menu-msg"), function (other) {
            if (other !== menu) other.hidden = true;
          });
          // The thread is a scroll container, so a menu opening upward from a message near the top
          // would be clipped by it. Flip to opening downward when there isn't room above.
          var room = e.target.getBoundingClientRect().top - el("messages").getBoundingClientRect().top;
          menu.classList.toggle("menu-up", room > 250);
          menu.hidden = !menu.hidden;
          return;
        }
        e.stopPropagation();
        if (menu) menu.hidden = true;

        if (act === "fork") {
          api("/api/messages/fork", { chat_id: Chat.activeId, message_id: m.id }).then(function (res) {
            toast("Branched — " + res.message_count + " message" + (res.message_count === 1 ? "" : "s") + " copied.");
            return Chat.refreshList().then(function () { return Chat.open(res.id); });
          }).catch(fail);
        }
        if (act === "copy" && navigator.clipboard) { navigator.clipboard.writeText(m.content || ""); toast("Copied"); }
        // Clicking the reaction already set clears it, so a mis-tap is undoable. A negative reaction asks for the
        // optional reason the app collects, since that is what makes the feedback useful later.
        if (act === "up" || act === "down") {
          var next = m.reaction === act ? "" : act;
          var send = function (reason) {
            return api("/api/messages/react", { id: m.id, chat_id: Chat.activeId, reaction: next, reason: reason || "" })
              .then(function () {
                m.reaction = next || null;
                meta.querySelectorAll(".react-btn").forEach(function (b) {
                  b.classList.toggle("on", b.getAttribute("data-act") === next);
                });
              }).catch(fail);
          };
          if (next === "down") {
            openModal("What went wrong?", [{ key: "reason", label: "Reason (optional)", value: m.feedback_reason || "" }],
              function (values) { return send(values.reason); });
          } else {
            send("");
          }
        }
        if (act === "save") {
          api("/api/saved-outputs", { content: m.content, source_chat_id: Chat.activeId, label: "From chat" })
            .then(function () { toast("Saved to Library"); }).catch(fail);
        }
        // Edit a question, or retry an answer. Both are "drop this turn and ask again": the
        // message rows are removed and the prompt is resent, which is what the app's own edit and
        // regenerate do to the active branch.
        if (act === "edit") {
          openModal("Edit message", [{ key: "text", label: "Message", type: "textarea", rows: 6, value: m.content || "" }],
            function (values) {
              var text = values.text.trim();
              if (!text) return;
              return Chat.replaceFrom(m, text);
            });
        }
        if (act === "retry") {
          var previousUser = Chat.previousUserMessage(m);
          if (!previousUser) { toast("Nothing to retry — no question above this answer.", true); return; }
          Chat.replaceFrom(previousUser, previousUser.content).catch(fail);
        }
        if (act === "delete") {
          confirmThen("Delete this message?", function () {
            return api("/api/messages/delete", { id: m.id, chat_id: Chat.activeId })
              .then(function () { wrap.remove(); });
          });
        }
      });
      wrap.appendChild(meta);
      el("thread").appendChild(wrap);
      return wrap;
    },

    /** `<img src>` cannot carry an Authorization header, so attachments are fetched with one and
     * handed to the element as a blob URL instead. */
    mediaNode: function (chatId, messageId, kind) {
      var node = document.createElement(kind === "image" ? "img" : "audio");
      node.className = "msg-media";
      if (kind === "audio") node.controls = true;
      fetch("/api/attachments?chat_id=" + encodeURIComponent(chatId) +
            "&message_id=" + encodeURIComponent(messageId) + "&kind=" + kind, { headers: authHeaders() })
        .then(function (res) { return res.ok ? res.blob() : Promise.reject(new Error("attachment missing")); })
        .then(function (blob) { node.src = URL.createObjectURL(blob); })
        .catch(function () { node.replaceWith(document.createTextNode("[attachment unavailable]")); });
      return node;
    },

    scrollToEnd: function () {
      var box = el("messages");
      box.scrollTop = box.scrollHeight;
    },

    // ---- sending ----------------------------------------------------------

    send: function () {
      var text = el("input").value.trim();
      if (!text && !Chat.attachments.length) return;
      if (Chat.abort) return;
      setGenerationState(true, "Preparing your request…", "The model is getting ready on this device.");
      setStatus("busy", "Preparing…");

      // A brand-new chat is created server-side (inheriting the active workspace's persona and
      // defaults), then immediately stamped with whatever the user had selected in the bar and
      // panel — otherwise the first message would use those choices but the chat would not
      // remember them on reopen.
      var ensureChat = Chat.activeId
        ? Promise.resolve(Chat.activeId)
        : api("/api/chats", { title: text.slice(0, 60) || "New chat" }).then(function (c) {
            Chat.activeId = c.id;
            Chat.applyConfig(c);
            Chat.saveConfig();
            return Chat.refreshList().then(function () { return c.id; });
          });

      ensureChat.then(function (chatId) {
        if (el("thread").querySelector(".chat-empty")) el("thread").innerHTML = "";

        // Optimistic user turn — the server persists the real row when generation finishes, and a
        // reload replaces this with it. Showing it immediately is what makes the box feel local.
        var userWrap = document.createElement("div");
        userWrap.className = "msg user";
        var userBody = document.createElement("div");
        userBody.className = "msg-body";
        Chat.attachments.forEach(function (a) {
          if (a.kind === "image") {
            var img = document.createElement("img");
            img.className = "msg-media"; img.src = a.dataUrl;
            userBody.appendChild(img);
          } else {
            var audio = document.createElement("audio");
            audio.className = "msg-media"; audio.controls = true; audio.src = a.dataUrl;
            userBody.appendChild(audio);
          }
        });
        var textNode = document.createElement("div");
        R.renderMarkdown(textNode, text);
        userBody.appendChild(textNode);
        userWrap.appendChild(userBody);
        el("thread").appendChild(userWrap);

        var content = [];
        if (text) content.push({ type: "text", text: text });
        Chat.attachments.forEach(function (a) {
          if (a.kind === "image") content.push({ type: "image_url", image_url: { url: a.dataUrl } });
          else content.push({ type: "input_audio", input_audio: { data: a.base64, format: a.format } });
        });

        var messages = [];
        var system = el("systemPrompt").value.trim();
        if (system) messages.push({ role: "system", content: system });
        messages.push({ role: "user", content: content.length === 1 && content[0].type === "text" ? text : content });

        var payload = {
          model: el("modelSelect").value,
          messages: messages,
          stream: true,
          stream_options: { include_usage: true },
          chat_id: chatId,
          temperature: parseFloat(el("temperature").value),
          top_p: parseFloat(el("topP").value),
          knowledge_base_ids: Chat.selectedKbIds(),
          app_tools: el("toolsToggle").checked,
          enabled_tools: Chat.selectedToolNames()
        };
        var maxTokens = parseInt(el("maxTokens").value, 10);
        if (maxTokens > 0) payload.max_tokens = maxTokens;
        var thinking = el("thinkingSelect").value;
        if (thinking) payload.thinking = thinking;

        el("input").value = "";
        Chat.attachments = [];
        Chat.renderAttachments();
        autoGrow();
        Chat.scrollToEnd();
        // The text is on its way to becoming a real message, so the stored draft is stale.
        clearTimeout(draftTimer);
        api("/api/chats/update", { id: chatId, draft: "" }).catch(function () { /* best effort */ });

        return Chat.stream(payload);
      }).catch(function (err) {
        setGenerationState(false);
        fail(err);
      });
    },

    /**
     * Reads the SSE response incrementally.
     *
     * `fetch` + a ReadableStream reader rather than EventSource because EventSource cannot send an
     * Authorization header or a POST body. Frames are split on the blank-line delimiter and any
     * trailing partial frame is carried into the next chunk, so a delta split across TCP reads is
     * never dropped or double-parsed.
     */
    stream: function (payload) {
      var wrap = document.createElement("div");
      wrap.className = "msg assistant";
      var body = document.createElement("div");
      body.className = "msg-body";
      var thinkNode = null, toolNodes = null;
      var textNode = document.createElement("div");
      // Until the first token lands there is nothing to show but the fact that something is
      // happening — the model may still be loading, which on a phone is not instant.
      textNode.innerHTML = '<span class="typing" aria-label="Generating"><i></i><i></i><i></i></span>';
      body.appendChild(textNode);
      wrap.appendChild(body);
      var stats = document.createElement("div");
      stats.className = "gen-stats";
      stats.hidden = true;
      wrap.appendChild(stats);
      var meta = document.createElement("div");
      meta.className = "msg-meta";
      wrap.appendChild(meta);
      el("thread").appendChild(wrap);
      Chat.scrollToEnd();

      var answer = "", reasoning = "", finishReason = null, streamError = null, lastStats = null;
      var controller = new AbortController();
      Chat.abort = controller;
      el("sendBtn").style.display = "none";
      el("stopBtn").style.display = "";
      setGenerationState(true, "Loading the local model…", "Generation runs on this device.");
      setStatus("busy", "Generating…");

      // The user is only pinned to the bottom while they are already there — auto-scrolling some-
      // one who has scrolled up to re-read an earlier answer is the classic streaming-chat bug.
      function maybeScroll() {
        var box = el("messages");
        if (box.scrollHeight - box.scrollTop - box.clientHeight < 130) box.scrollTop = box.scrollHeight;
      }

      return fetch("/v1/chat/completions", {
        method: "POST", headers: jsonHeaders(), body: JSON.stringify(payload), signal: controller.signal
      }).then(function (res) {
        if (res.status === 401) { setStatus("auth"); throw new Error("This server needs an API key."); }
        if (!res.ok) {
          return res.text().then(function (raw) {
            var parsed = tryParse(raw) || {};
            throw new Error((parsed.error && parsed.error.message) || ("Request failed (HTTP " + res.status + ")"));
          });
        }
        var reader = res.body.getReader();
        var decoder = new TextDecoder();
        var buffer = "";

        function pump() {
          return reader.read().then(function (chunk) {
            if (chunk.done) return;
            buffer += decoder.decode(chunk.value, { stream: true });
            var frames = buffer.split("\n\n");
            buffer = frames.pop();          // keep the trailing partial frame
            frames.forEach(handleFrame);
            maybeScroll();
            return pump();
          });
        }

        function handleFrame(frame) {
          var lines = frame.split("\n");
          for (var i = 0; i < lines.length; i++) {
            var line = lines[i];
            if (line.indexOf("data:") !== 0) continue;   // ':' comments and blank lines
            var data = line.slice(5).trim();
            if (data === "[DONE]") return;
            var json = tryParse(data);
            if (!json) continue;
            if (json.error) { streamError = json.error.message || "Generation failed"; continue; }
            // Live telemetry from the device: tokens, rate and free RAM, the same readout the
            // app's own generation-stats strip shows while a reply streams.
            if (json.vervan_stats) {
              lastStats = json.vervan_stats;
              renderGenStats(stats, lastStats, true);
              setGenerationState(true, "Generating response…", "Live device progress is updating below the reply.");
              continue;
            }
            if (json.vervan_tool) {
              setGenerationState(true, "Using a local tool…", "Waiting for the device to finish the approved action.");
              if (!toolNodes) { toolNodes = document.createElement("div"); body.insertBefore(toolNodes, textNode); }
              var t = document.createElement("div");
              t.className = "msg-tool" + (json.vervan_tool.success === false ? " failed" : "");
              t.innerHTML = icon("tool") + "<b>" + esc(json.vervan_tool.name || "tool") + "</b> " + esc(json.vervan_tool.summary || "");
              toolNodes.appendChild(t);
            }
            var choice = (json.choices || [])[0];
            if (!choice) continue;
            var delta = choice.delta || {};
            if (delta.reasoning_content) {
              setGenerationState(true, "Thinking through the request…", "The response is being composed locally.");
              reasoning += delta.reasoning_content;
              if (!thinkNode) { thinkNode = thinkingNode(""); body.insertBefore(thinkNode, body.firstChild); }
              thinkNode.querySelector(".think-body").textContent = reasoning;
            }
            if (delta.content) {
              setGenerationState(true, "Generating response…", "The response is being composed locally.");
              answer += delta.content;
              R.renderMarkdown(textNode, answer);
            }
            if (delta.tool_calls) {
              if (!toolNodes) { toolNodes = document.createElement("div"); body.insertBefore(toolNodes, textNode); }
              delta.tool_calls.forEach(function (call) {
                var t = document.createElement("div");
                t.className = "msg-tool";
              t.innerHTML = icon("tool") + "<b>" + esc((call.function && call.function.name) || "tool") + "</b> requested by the model";
                toolNodes.appendChild(t);
              });
            }
            if (choice.finish_reason) finishReason = choice.finish_reason;
          }
        }

        return pump();
      }).then(function () {
        if (streamError) throw new Error(streamError);
        if (!answer && !reasoning) textNode.innerHTML = '<span class="row-sub">The model returned nothing.</span>';
        // The spinner comes off but the numbers stay: the final rate is worth keeping on screen.
        renderGenStats(stats, lastStats, false);
        if (finishReason === "length") {
          meta.innerHTML = '<span class="chip warn">Stopped at the output limit</span>';
        }
        setStatus("ok");
        return Chat.refreshList();
      }).catch(function (err) {
        if (err.name === "AbortError") {
          meta.innerHTML = '<span class="chip">Stopped</span>';
          renderGenStats(stats, lastStats, false);
          setStatus("ok");
          return Chat.refreshList();
        }
        stats.hidden = true;
        setStatus(/API key/.test(err.message) ? "auth" : "err");
        textNode.innerHTML = '<div class="stream-error" role="alert"><span class="stream-error-copy">' + esc(err.message || "Generation failed") + "</span></div>";
        var retry = document.createElement("button");
        retry.type = "button";
        retry.className = "secondary retry-inline";
        retry.textContent = "Try again";
        retry.addEventListener("click", function () {
          if (Chat.abort) return;
          retry.disabled = true;
          retry.textContent = "Retrying…";
          Chat.stream(payload);
        });
        textNode.querySelector(".stream-error").appendChild(retry);
      }).then(function () {
        Chat.abort = null;
        el("sendBtn").style.display = "";
        el("stopBtn").style.display = "none";
        setGenerationState(false);
      });
    }
  };

  /**
   * Renders one generation-stats line: rate, tokens, elapsed, and the device's free RAM.
   *
   * RAM is included because this runs on a phone — a reply slowing to a crawl is usually memory
   * pressure, and seeing "180/3800 MB free" turns a mysterious stall into an explicable one. The
   * line turns amber when the OS reports low memory.
   */
  function renderGenStats(node, stats, live) {
    if (!stats) return;
    var memory = stats.memory || {};
    var parts = [];
    if (stats.tokens_per_second) parts.push("<b>" + stats.tokens_per_second.toFixed(1) + "</b> tok/s");
    if (stats.tokens) parts.push("<b>" + stats.tokens + "</b> tokens");
    if (stats.elapsed_ms) parts.push("<b>" + (stats.elapsed_ms / 1000).toFixed(1) + "</b>s");
    if (memory.total_mb) parts.push("<b>" + memory.available_mb + "</b>/" + memory.total_mb + " MB free");
    node.className = "gen-stats" + (memory.low ? " low" : "");
    node.innerHTML = (live ? '<span class="spinner"></span>' : "") +
      parts.join('<span class="sep">·</span>');
    node.hidden = !parts.length;
  }

  function thinkingNode(text) {
    var node = document.createElement("details");
    node.className = "msg-thinking";
    node.innerHTML = "<summary>Reasoning</summary><div class='think-body'></div>";
    node.querySelector(".think-body").textContent = text;
    return node;
  }
  /** A stored assistant message keeps its `<think>` block inline; the live stream splits reasoning
   * out into its own delta. Both end up in the same collapsible block. */
  function splitThinking(content) {
    var m = /^\s*<think>([\s\S]*?)<\/think>\s*/i.exec(content);
    if (!m) return { thinking: "", answer: content };
    return { thinking: m[1].trim(), answer: content.slice(m[0].length) };
  }
  function tryParse(raw) { try { return JSON.parse(raw); } catch (e) { return null; } }

  el("newChatBtn").addEventListener("click", function () {
    Chat.activeId = null;
    Chat.config = null;
    Chat.messages = [];
    el("chatTitle").textContent = "New chat";
    el("incognitoChip").hidden = true;
    el("input").value = "";
    el("optsBody").style.display = "none";
    el("optsNoChat").style.display = "";
    Chat.renderList();
    Chat.renderEmpty();
    el("input").focus();
    closeLists();
  });
  el("chatSearch").addEventListener("input", function () { Chat.renderList(); });
  el("optsToggle").addEventListener("click", function () {
    var open = el("optsCol").classList.toggle("hidden") === false;
    el("optsToggle").setAttribute("aria-expanded", String(open));
  });
  el("optsClose").addEventListener("click", function () {
    el("optsCol").classList.add("hidden");
    el("optsToggle").setAttribute("aria-expanded", "false");
  });
  el("sendBtn").addEventListener("click", function () { Chat.send(); });
  el("stopBtn").addEventListener("click", function () { if (Chat.abort) Chat.abort.abort(); });
  el("temperature").addEventListener("input", function () {
    el("temperatureValue").textContent = parseFloat(this.value).toFixed(2);
    if (!Chat.hydrating) Chat.touched.temperature = true;
  });
  el("topP").addEventListener("input", function () {
    el("topPValue").textContent = parseFloat(this.value).toFixed(2);
    if (!Chat.hydrating) Chat.touched.topP = true;
  });

  // Every control that belongs to the chat persists on change. The model and thinking selects sit
  // in the top bar rather than the panel, but they are chat settings just the same.
  ["modelSelect", "thinkingSelect", "personaSelect", "projectSelect", "folderSelect", "profileSelect",
   "toolsToggle", "archivedToggle", "ragToggle", "temperature", "topP", "topK"].forEach(function (id) {
    el(id).addEventListener("change", function () {
      if (id === "modelSelect") Chat.syncCapabilities();
      if (id === "ragToggle") el("ragHint").textContent = this.checked ? "Answers will cite your documents" : "";
      Chat.saveConfig();
    });
  });
  el("kbCheckList").addEventListener("change", function () {
    if (!el("ragToggle").checked) el("ragToggle").checked = true;
    Chat.saveConfig();
  });

  // ---- chat overflow menu -------------------------------------------------
  function closeMenu() { el("chatMenu").hidden = true; }
  el("chatMenuBtn").addEventListener("click", function (e) {
    e.stopPropagation();
    var menu = el("chatMenu");
    menu.hidden = !menu.hidden;
    if (!menu.hidden) {
      // Actions that need a saved chat, or a previous title to go back to, are disabled rather
      // than silently doing nothing when clicked.
      var hasChat = !!Chat.activeId;
      menu.querySelectorAll("button").forEach(function (b) { b.disabled = !hasChat; });
      var restore = menu.querySelector('[data-act="restore-title"]');
      if (restore) restore.disabled = !hasChat || !(Chat.config && Chat.config.previous_title);
    }
  });
  document.addEventListener("click", closeMenu);
  document.addEventListener("keydown", function (e) { if (e.key === "Escape") closeMenu(); });
  el("chatMenu").addEventListener("click", function (e) {
    e.stopPropagation();
    var act = e.target.getAttribute && e.target.getAttribute("data-act");
    if (!act || e.target.disabled) return;
    closeMenu();
    Chat.menuAction(act);
  });

  Chat.transcript = function (asMarkdown) {
    return (Chat.messages || []).map(function (m) {
      var who = m.role === "user" ? "You" : (m.role === "assistant" ? "Assistant" : "System");
      return asMarkdown ? "**" + who + "**\n\n" + (m.content || "") : who + ": " + (m.content || "");
    }).join(asMarkdown ? "\n\n---\n\n" : "\n\n");
  };
  function download(name, text, mime) {
    var blob = new Blob([text], { type: mime });
    var url = URL.createObjectURL(blob);
    var link = document.createElement("a");
    link.href = url; link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
  }

  Chat.menuAction = function (act) {
    var id = Chat.activeId;
    var config = Chat.config || {};
    var title = config.title || "chat";
    var safeName = title.replace(/[^\w.\- ]+/g, "_").slice(0, 60) || "chat";

    // Export, copy and info are computed from the messages already loaded — no round trip for
    // something the browser is holding.
    if (act === "export-md") return download(safeName + ".md", "# " + title + "\n\n" + Chat.transcript(true), "text/markdown");
    if (act === "export-txt") return download(safeName + ".txt", title + "\n\n" + Chat.transcript(false), "text/plain");
    if (act === "copy-all") {
      if (navigator.clipboard) navigator.clipboard.writeText(Chat.transcript(false)).then(function () { toast("Transcript copied"); });
      return;
    }
    if (act === "stats") {
      var messages = Chat.messages || [];
      var users = messages.filter(function (m) { return m.role === "user"; }).length;
      var replies = messages.filter(function (m) { return m.role === "assistant"; }).length;
      var tokens = messages.reduce(function (sum, m) { return sum + (m.token_count || 0); }, 0);
      var characters = messages.reduce(function (sum, m) { return sum + (m.content || "").length; }, 0);
      var persona = Chat.personas.find(function (p) { return p.id === config.persona_id; });
      el("modal").innerHTML = "<h2>Chat info</h2>" +
        '<div class="card-grid">' +
        [["Messages", messages.length], ["Questions", users], ["Replies", replies],
         ["Tokens (est.)", tokens], ["Characters", characters]]
          .map(function (p) { return '<div class="stat"><b>' + p[1] + "</b><span>" + p[0] + "</span></div>"; }).join("") +
        "</div>" +
        '<p class="row-sub" style="margin-top:12px">Profile: ' + esc(config.profile || "BALANCED") +
        " · Persona: " + esc(persona ? persona.name : "none") +
        " · Thinking: " + esc(config.thinking_mode || "model default") + "</p>" +
        '<div class="modal-actions"><button class="primary" type="button" id="mCancel">Close</button></div>';
      el("modalBack").className = "modal-back show";
      el("mCancel").addEventListener("click", closeModal);
      return;
    }

    if (act === "rename") {
      return openModal("Rename chat", [{ key: "title", label: "Title", value: title }], function (v) {
        return api("/api/chats/update", { id: id, title: v.title }).then(Chat.afterConfigChange);
      });
    }
    if (act === "pin") {
      return api("/api/chats/update", { id: id, pinned: !config.pinned }).then(Chat.afterConfigChange).catch(fail);
    }
    if (act === "incognito") {
      return api("/api/chats/update", { id: id, is_temporary: !config.is_temporary })
        .then(function (updated) {
          toast(updated.is_temporary
            ? "Incognito on — this chat won't be backed up or searched, and is removed when closed."
            : "Incognito off.");
          return Chat.afterConfigChange(updated);
        }).catch(fail);
    }
    if (act === "generate-title") {
      toast("Asking the model for a title…");
      return api("/api/chats/generate-title", { id: id })
        .then(function (res) { toast("Renamed to “" + res.title + "”"); return Chat.open(id).then(Chat.refreshList); })
        .catch(fail);
    }
    if (act === "restore-title") {
      return api("/api/chats/restore-title", { id: id })
        .then(function () { return Chat.open(id).then(Chat.refreshList); }).catch(fail);
    }
    if (act === "duplicate") {
      return api("/api/chats/duplicate", { id: id }).then(function (res) {
        toast("Duplicated");
        return Chat.refreshList().then(function () { return Chat.open(res.id); });
      }).catch(fail);
    }
    if (act === "to-kb") {
      if (!Chat.kbs.length) { toast("Create a knowledge base first.", true); return; }
      return openModal("Add this chat to a knowledge base", [{
        key: "kb", label: "Knowledge base", type: "select",
        value: Chat.kbs[0].id,
        options: Chat.kbs.map(function (kb) { return { value: kb.id, label: kb.name }; })
      }], function (values) {
        return api("/api/chats/add-to-knowledge-base", { chat_id: id, knowledge_base_id: values.kb })
          .then(function () { toast("Filed — it'll be searchable once indexing finishes."); });
      });
    }
    if (act === "reset") {
      return confirmThen("Reset this chat's model, persona, thinking, tools and sampling to their defaults?", function () {
        // Explicit empty values, not omitted keys: the update endpoint treats an absent key as
        // "leave alone" and an explicit empty one as "clear", which is what reset needs.
        return api("/api/chats/update", {
          id: id, model_id: "", persona_id: "", thinking_mode: "", profile: "BALANCED",
          tools_enabled: false, knowledge_base_ids: [], temperature: null, top_p: null, top_k: null
        }).then(function () { return Chat.open(id); });
      });
    }
    if (act === "delete") {
      return confirmThen("Move “" + title + "” to the recycle bin?", function () {
        return api("/api/chats/delete", { id: id }).then(function () {
          Chat.activeId = null; Chat.config = null;
          Chat.renderEmpty();
          el("chatTitle").textContent = "New chat";
          return Chat.refreshList();
        });
      });
    }
  };

  Chat.afterConfigChange = function (updated) {
    if (updated && updated.id) Chat.applyConfig(updated);
    return Chat.refreshList();
  };

  // ---- draft persistence --------------------------------------------------
  // The composer's unsent text is stored on the chat row, so an interrupted message survives a
  // reload and shows up on the phone too — the app does exactly this with Chat.draft.
  var draftTimer = null;
  el("input").addEventListener("input", function () {
    if (!Chat.activeId) return;
    clearTimeout(draftTimer);
    var value = el("input").value;
    draftTimer = setTimeout(function () {
      api("/api/chats/update", { id: Chat.activeId, draft: value }).catch(function () { /* best effort */ });
    }, 900);
  });

  function autoGrow() {
    var input = el("input");
    input.style.overflowY = "hidden";
    input.style.height = "auto";
    var height = Math.min(input.scrollHeight, 190);
    input.style.height = height + "px";
    if (input.scrollHeight > height) input.style.overflowY = "auto";
  }
  el("input").addEventListener("input", autoGrow);
  el("input").addEventListener("keydown", function (e) {
    if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); Chat.send(); }
  });

  // ---- attachments --------------------------------------------------------
  // Four kinds, because they do genuinely different things: an image or audio clip rides along
  // with the message to the model, a document is imported and retrieved against, and OCR never
  // reaches the model at all — it just puts extracted text in the composer.
  var attachMode = "image";
  var ATTACH_ACCEPT = {
    image: "image/*",
    audio: "audio/*",
    ocr: "image/*",
    document: ".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.epub,.html,.htm,.csv,.txt,.md,.rtf,image/*"
  };
  el("attachBtn").addEventListener("click", function (e) {
    e.stopPropagation();
    var menu = el("attachMenu");
    menu.hidden = !menu.hidden;
    if (!menu.hidden) {
      // Documents and OCR need a saved chat to attach to; image and audio do not, since they
      // travel with the message that creates the chat.
      menu.querySelector('[data-attach="document"]').disabled = !Chat.activeId;
    }
  });
  el("attachMenu").addEventListener("click", function (e) {
    e.stopPropagation();
    var mode = e.target.getAttribute && e.target.getAttribute("data-attach");
    if (!mode || e.target.disabled) return;
    el("attachMenu").hidden = true;
    attachMode = mode;
    var input = el("fileInput");
    input.accept = ATTACH_ACCEPT[mode];
    input.multiple = mode === "image" || mode === "audio";
    input.click();
  });
  document.addEventListener("click", function () {
    el("attachMenu").hidden = true;
    Array.prototype.forEach.call(document.querySelectorAll(".menu-msg"), function (menu) { menu.hidden = true; });
  });

  el("fileInput").addEventListener("change", function () {
    var files = Array.prototype.slice.call(this.files);
    this.value = "";
    if (attachMode === "document") files.forEach(attachDocument);
    else if (attachMode === "ocr") files.forEach(runOcr);
    else files.forEach(addAttachment);
  });

  function readBase64(file) {
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onload = function () {
        var url = String(reader.result);
        resolve({ dataUrl: url, base64: url.slice(url.indexOf(",") + 1) });
      };
      reader.onerror = function () { reject(new Error("Could not read " + file.name)); };
      reader.readAsDataURL(file);
    });
  }

  /** Imports a file into a chat-scoped knowledge base and turns on grounding — the same thing the
   * app's paperclip → Document does, so the next message answers from it with sources. */
  function attachDocument(file) {
    if (!Chat.activeId) { toast("Send a message first, then attach a document to the chat.", true); return; }
    toast("Importing " + file.name + "…");
    readBase64(file).then(function (read) {
      return api("/api/chats/attach-document", {
        chat_id: Chat.activeId, name: file.name, data: read.base64
      }, { timeoutMs: 180000 });
    }).then(function (res) {
      toast(res.grounded
        ? file.name + " attached — answers will cite it."
        : file.name + " attached (keyword search only; no embedding model is loaded).");
      // The server turned grounding on and added the knowledge base, so the panel must catch up.
      return Chat.open(Chat.activeId);
    }).catch(fail);
  }

  /** Device-side text extraction. The result lands in the composer for the user to edit rather
   * than being sent anywhere, which is what the app does with a scanned page. */
  function runOcr(file) {
    toast("Reading text from " + file.name + "…");
    readBase64(file).then(function (read) {
      return api("/api/ocr", { data: read.base64 }, { timeoutMs: 120000 });
    }).then(function (res) {
      if (res.empty) { toast("No text found in that image.", true); return; }
      var input = el("input");
      input.value = input.value ? input.value + "\n\n" + res.text : res.text;
      autoGrow();
      input.focus();
      toast("Text extracted into the message box.");
    }).catch(fail);
  }
  function addAttachment(file) {
    var isImage = /^image\//.test(file.type);
    var isAudio = /^audio\//.test(file.type);
    if (!isImage && !isAudio) { toast("Only images and audio can be attached here.", true); return; }
    // A warning, not a block. These capability flags are frequently unset on an imported model, so
    // refusing the attachment on their say-so blocked images on models that handle them perfectly
    // well. The server still rejects a genuinely unsupported attachment with a clear message.
    var model = Chat.model || {};
    if (isImage && !model.supports_vision) toast("This model isn't marked as supporting images — sending anyway.");
    if (isAudio && !model.supports_audio) toast("This model isn't marked as supporting audio — sending anyway.");
    // The engine bridge takes one image per request, so a second would be silently dropped —
    // refused up front instead, with the reason.
    if (isImage && Chat.attachments.some(function (a) { return a.kind === "image"; })) {
      toast("One image per message — the model takes a single image at a time.", true);
      return;
    }
    var reader = new FileReader();
    reader.onload = function () {
      var dataUrl = String(reader.result);
      Chat.attachments.push({
        kind: isImage ? "image" : "audio",
        name: file.name,
        dataUrl: dataUrl,
        base64: dataUrl.slice(dataUrl.indexOf(",") + 1),
        format: (file.type.split("/")[1] || "wav").replace(/;.*$/, "")
      });
      Chat.renderAttachments();
    };
    reader.readAsDataURL(file);
  }
  Chat.renderAttachments = function () {
    el("attachments").innerHTML = Chat.attachments.map(function (a, i) {
      return '<span class="attachment">' +
        (a.kind === "image" ? '<img src="' + a.dataUrl + '" alt="">' : icon("audio")) +
        esc(a.name) + '<button class="icon-btn" data-i="' + i + '" aria-label="Remove attachment" title="Remove attachment">' + icon("close") + "</button></span>";
    }).join("");
    Array.prototype.forEach.call(el("attachments").querySelectorAll("button"), function (b) {
      b.addEventListener("click", function () {
        Chat.attachments.splice(parseInt(b.getAttribute("data-i"), 10), 1);
        Chat.renderAttachments();
      });
    });
  };
  // Paste an image straight into the composer, the way every desktop chat client works.
  el("input").addEventListener("paste", function (e) {
    var items = (e.clipboardData || {}).items || [];
    for (var i = 0; i < items.length; i++) {
      if (items[i].kind === "file") {
        var file = items[i].getAsFile();
        if (file) { addAttachment(file); e.preventDefault(); }
      }
    }
  });

  // ================================================================ LIBRARY

  /**
   * One generic list/detail controller for eight entity types. Each entry declares its endpoint,
   * the JSON key its list comes back under, how to title a row, and its editor fields; everything
   * else — fetching, rendering, the New button, save and delete — is shared. Adding another
   * library type is a table entry, not another screen.
   */
  var LIB = {
    notes: {
      label: "Note", path: "/api/notes", key: "notes",
      title: function (n) { return n.title; },
      sub: function (n) { return relativeTime(n.updated_at); },
      fields: function (n) {
        return [
          { key: "title", label: "Title", value: (n && n.title) || "" },
          { key: "content", label: "Content", type: "textarea", rows: 10, value: (n && n.content) || "" },
          { key: "tags", label: "Tags (comma separated)", value: (n && n.tags) || "" }
        ];
      },
      detail: function (n) { return markdownDetail(n.title, n.content); }
    },
    memories: {
      label: "Memory", path: "/api/memories", key: "memories",
      title: function (m) { return m.text.slice(0, 70); },
      sub: function (m) { return m.scope.toLowerCase() + (m.enabled ? "" : " · off"); },
      fields: function (m) {
        return [
          { key: "text", label: "What should the model remember?", type: "textarea", rows: 4, value: (m && m.text) || "" },
          { key: "scope", label: "Scope", type: "select", value: (m && m.scope) || "GLOBAL",
            options: [{ value: "GLOBAL", label: "Global" }, { value: "PERSONA", label: "Persona" }, { value: "PROJECT", label: "Project" }] },
          { key: "key", label: "Dedup key (optional)", value: (m && m.key) || "" },
          { key: "enabled", label: "Enabled", type: "checkbox", value: m ? m.enabled : true }
        ];
      },
      detail: function (m) {
        return "<h2>Memory</h2><p>" + esc(m.text) + "</p>" +
          '<p class="row-sub">Scope: ' + esc(m.scope) + (m.key ? " · key: " + esc(m.key) : "") +
          " · " + (m.enabled ? "enabled" : "disabled") + "</p>";
      }
    },
    personas: {
      label: "Persona", path: "/api/personas", key: "personas",
      title: function (p) { return p.name; },
      sub: function (p) { return p.built_in ? "Built-in" : p.description; },
      fields: function (p) {
        return [
          { key: "name", label: "Name", value: (p && p.name) || "" },
          { key: "description", label: "Description", value: (p && p.description) || "" },
          { key: "system_instruction", label: "System instruction", type: "textarea", rows: 8, value: (p && p.system_instruction) || "" },
          { key: "tone", label: "Tone", type: "select", value: (p && p.tone) || "NEUTRAL",
            options: ["WARM", "NEUTRAL", "DIRECT", "PLAYFUL"].map(opt) },
          { key: "formality", label: "Formality", type: "select", value: (p && p.formality) || "NEUTRAL",
            options: ["CASUAL", "NEUTRAL", "FORMAL"].map(opt) },
          { key: "conciseness", label: "Conciseness", type: "select", value: (p && p.conciseness) || "NORMAL",
            options: ["TERSE", "NORMAL", "ELABORATE"].map(opt) },
          { key: "response_length", label: "Response length", type: "select", value: (p && p.response_length) || "BALANCED",
            options: ["SHORT", "BALANCED", "LONG"].map(opt) },
          { key: "language", label: "Preferred reply language", value: (p && p.language) || "" }
        ];
      },
      detail: function (p) {
        return "<h2>" + esc(p.name) + "</h2><p>" + esc(p.description) + "</p>" +
          '<div class="card"><h3>System instruction</h3><p style="white-space:pre-wrap">' + esc(p.system_instruction) + "</p></div>" +
          '<p class="row-sub">' + [p.tone, p.formality, p.conciseness, p.response_length].map(esc).join(" · ") + "</p>";
      }
    },
    templates: {
      label: "Prompt template", path: "/api/templates", key: "templates",
      title: function (t) { return "/" + t.name; },
      sub: function (t) { return t.built_in ? "Built-in" : t.description; },
      fields: function (t) {
        return [
          { key: "name", label: "Command name (without /)", value: (t && t.name) || "" },
          { key: "description", label: "Description", value: (t && t.description) || "" },
          { key: "body", label: "Body — use {{input}} for the user's text", type: "textarea", rows: 8, value: (t && t.body) || "" }
        ];
      },
      detail: function (t) {
        return "<h2>/" + esc(t.name) + "</h2><p>" + esc(t.description) + "</p>" +
          '<div class="card"><pre style="white-space:pre-wrap;margin:0">' + esc(t.body) + "</pre></div>";
      }
    },
    "saved-outputs": {
      label: "Saved output", path: "/api/saved-outputs", key: "saved_outputs",
      title: function (s) { return s.label || s.content.slice(0, 60); },
      sub: function (s) { return relativeTime(s.created_at); },
      fields: function (s) {
        return [
          { key: "label", label: "Label", value: (s && s.label) || "" },
          { key: "content", label: "Content", type: "textarea", rows: 10, value: (s && s.content) || "" }
        ];
      },
      detail: function (s) { return markdownDetail(s.label || "Saved output", s.content); }
    },
    projects: {
      label: "Project", path: "/api/projects", key: "projects",
      title: function (p) { return p.name; },
      sub: function (p) { return relativeTime(p.created_at); },
      fields: function (p) {
        return [
          { key: "name", label: "Name", value: (p && p.name) || "" },
          { key: "instructions", label: "Instructions", type: "textarea", rows: 8, value: (p && p.instructions) || "" }
        ];
      },
      detail: function (p) { return markdownDetail(p.name, p.instructions); }
    },
    workspaces: {
      label: "Workspace", path: "/api/workspaces", key: "workspaces",
      title: function (w) { return w.name + (w.is_default ? " (default)" : ""); },
      sub: function (w) { return w.archived ? "Archived" : w.description; },
      fields: function (w) {
        return [
          { key: "name", label: "Name", value: (w && w.name) || "" },
          { key: "description", label: "Description", value: (w && w.description) || "" },
          { key: "auto_title", label: "Generate chat titles automatically", type: "checkbox", value: w ? w.auto_title : false }
        ];
      },
      detail: function (w) {
        return "<h2>" + esc(w.name) + "</h2><p>" + esc(w.description) + "</p>" +
          '<p class="row-sub">' + (w.is_default ? "Default workspace · " : "") +
          (w.archived ? "archived" : "active") + "</p>";
      }
    },
    folders: {
      label: "Folder", path: "/api/folders", key: "folders",
      title: function (f) { return f.name; },
      sub: function (f) { return relativeTime(f.created_at); },
      fields: function (f) {
        return [
          { key: "name", label: "Name", value: (f && f.name) || "" },
          { key: "color", label: "Colour (hex)", value: (f && f.color) || "#E8A33D" }
        ];
      },
      detail: function (f) { return "<h2>" + esc(f.name) + "</h2><p class='row-sub'>Colour " + esc(f.color) + "</p>"; }
    }
  };
  function opt(v) { return { value: v, label: v.charAt(0) + v.slice(1).toLowerCase() }; }
  function markdownDetail(title, content) {
    return "<h2>" + esc(title) + '</h2><div class="card" id="mdTarget"></div>';
  }

  var Library = {
    kind: "notes", items: [], activeId: null, pendingKind: null,

    load: function () {
      if (Library.pendingKind && LIB[Library.pendingKind]) {
        Library.kind = Library.pendingKind;
        el("libKind").value = Library.kind;
      }
      Library.pendingKind = null;
      var spec = LIB[Library.kind];
      Library.renderTabs();
      el("libList").innerHTML = skeletonRows(6);
      return tracked(api(spec.path)).then(function (data) {
        Library.items = data[spec.key] || [];
        Library.renderList();
        if (Library.activeId) Library.select(Library.activeId);
        else el("libDetail").innerHTML = '<div class="empty-note">Pick a ' + esc(spec.label.toLowerCase()) + " to view it.</div>";
      }).catch(function (err) {
        showLoadError("libList", "Library is unavailable", err, Library.load);
        fail(err);
      });
    },

    /** The kind tabs replaced the old dropdown; the hidden <select> is kept as the value holder so
     * nothing that reads Library.kind or the select had to change. */
    renderTabs: function () {
      Array.prototype.forEach.call(el("libTabs").querySelectorAll("button"), function (button) {
        button.classList.toggle("active", button.getAttribute("data-kind") === Library.kind);
      });
    },

    renderList: function () {
      var spec = LIB[Library.kind];
      el("libList").innerHTML = Library.items.length ? Library.items.map(function (item) {
        return '<div class="row' + (item.id === Library.activeId ? " active" : "") + '" data-id="' + esc(item.id) + '">' +
          '<div class="row-title">' + esc(spec.title(item) || "Untitled") + "</div>" +
          '<div class="row-sub">' + esc(spec.sub(item) || "") + "</div>" +
          '<div class="row-actions">' +
            (item.built_in ? "" : '<button class="icon-btn" data-act="edit" aria-label="Edit" title="Edit">' + icon("edit") + "</button>") +
            (item.built_in || item.is_default ? "" : '<button class="icon-btn destructive" data-act="delete" aria-label="Delete" title="Delete">' + icon("trash") + "</button>") +
          "</div></div>";
      }).join("") : '<div class="empty-note">Nothing here yet.</div>';

      Array.prototype.forEach.call(el("libList").querySelectorAll(".row"), function (row) {
        var id = row.getAttribute("data-id");
        var item = Library.items.find(function (i) { return i.id === id; });
        row.addEventListener("click", function (e) {
          var act = e.target.getAttribute && e.target.getAttribute("data-act");
          if (!act) { Library.select(id); closeLists(); return; }
          e.stopPropagation();
          if (act === "edit") Library.edit(item);
          else confirmThen("Delete this " + LIB[Library.kind].label.toLowerCase() + "?", function () {
            return api(LIB[Library.kind].path + "/delete", { id: id }).then(function () {
              if (Library.activeId === id) Library.activeId = null;
              return Library.load();
            });
          });
        });
      });
    },

    select: function (id) {
      Library.activeId = id;
      var item = Library.items.find(function (i) { return i.id === id; });
      if (!item) { el("libDetail").innerHTML = ""; return; }
      var spec = LIB[Library.kind];
      el("libDetail").innerHTML = spec.detail(item) +
        '<div class="modal-actions"><button class="secondary" type="button" id="libEditBtn">Edit</button></div>';
      var target = el("mdTarget");
      if (target) R.renderMarkdown(target, item.content || item.instructions || "");
      var editBtn = el("libEditBtn");
      if (editBtn) editBtn.addEventListener("click", function () { Library.edit(item); });
      Library.renderList();
    },

    edit: function (item) {
      var spec = LIB[Library.kind];
      openModal((item ? "Edit " : "New ") + spec.label.toLowerCase(), spec.fields(item), function (values) {
        var payload = Object.assign({}, values);
        if (item) payload.id = item.id;
        return api(spec.path, payload).then(function () {
          toast(spec.label + " saved");
          return Library.load();
        });
      });
    }
  };
  el("libTabs").addEventListener("click", function (e) {
    var kind = e.target.getAttribute && e.target.getAttribute("data-kind");
    if (!kind || kind === Library.kind) return;
    Library.kind = kind;
    el("libKind").value = kind;
    Library.activeId = null;
    Library.load();
  });
  el("libNewBtn").addEventListener("click", function () { Library.edit(null); });

  // ================================================================ KNOWLEDGE

  var Knowledge = {
    kbs: [], activeId: null, pendingOpen: null,

    load: function () {
      if (!Knowledge.kbs.length) el("kbList").innerHTML = skeletonRows(5);
      return tracked(api("/api/knowledge-bases")).then(function (data) {
        Knowledge.kbs = data.data || [];
        Knowledge.renderList();
        if (Knowledge.pendingOpen) { Knowledge.activeId = Knowledge.pendingOpen; Knowledge.pendingOpen = null; }
        if (Knowledge.activeId) Knowledge.select(Knowledge.activeId);
        else el("kbDetail").innerHTML = '<div class="empty-note">Pick a knowledge base, or create one.</div>';
      }).catch(function (err) {
        showLoadError("kbList", "Knowledge bases are unavailable", err, Knowledge.load);
        fail(err);
      });
    },

    renderList: function () {
      var q = el("kbSearch").value.trim().toLowerCase();
      var shown = Knowledge.kbs.filter(function (kb) { return !q || kb.name.toLowerCase().indexOf(q) >= 0; });
      el("kbList").innerHTML = shown.length ? shown.map(function (kb) {
        return '<div class="row' + (kb.id === Knowledge.activeId ? " active" : "") + '" data-id="' + esc(kb.id) + '">' +
          '<div class="row-title">' + esc(kb.name) + "</div>" +
          '<div class="row-sub">' + kb.document_count + " document" + (kb.document_count === 1 ? "" : "s") + "</div>" +
          '<div class="row-actions"><button class="icon-btn destructive" data-act="delete" aria-label="Delete" title="Delete">' + icon("trash") + "</button></div></div>";
      }).join("") : '<div class="empty-note">No knowledge bases yet.</div>';

      Array.prototype.forEach.call(el("kbList").querySelectorAll(".row"), function (row) {
        var id = row.getAttribute("data-id");
        row.addEventListener("click", function (e) {
          if (e.target.getAttribute && e.target.getAttribute("data-act") === "delete") {
            e.stopPropagation();
            confirmThen("Delete this knowledge base and its documents?", function () {
              return api("/api/knowledge-bases/delete", { id: id }).then(function () {
                if (Knowledge.activeId === id) Knowledge.activeId = null;
                return Knowledge.load();
              });
            });
            return;
          }
          Knowledge.select(id);
          closeLists();
        });
      });
    },

    select: function (id) {
      Knowledge.activeId = id;
      Knowledge.renderList();
      var kb = Knowledge.kbs.find(function (k) { return k.id === id; });
      if (!kb) return;
      el("kbDetail").innerHTML =
        "<h2>" + esc(kb.name) + "</h2><p>" + esc(kb.description || "") + "</p>" +
        '<div class="modal-actions" style="justify-content:flex-start;margin:0 0 14px">' +
        '<button class="secondary" type="button" id="kbEditBtn">Rename</button></div>' +
        '<div class="dropzone" id="dropzone">Drop files here, or click to choose.<br>' +
        '<span class="row-sub">PDF, Word, PowerPoint, Excel, EPUB, HTML, CSV, text and images.</span></div>' +
        '<input type="file" id="docInput" multiple hidden>' +
        '<div class="card" style="margin-top:14px"><h3>Documents</h3><div id="docList"></div></div>';

      el("kbEditBtn").addEventListener("click", function () { Knowledge.edit(kb); });
      var dropzone = el("dropzone");
      dropzone.addEventListener("click", function () { el("docInput").click(); });
      dropzone.addEventListener("dragover", function (e) { e.preventDefault(); dropzone.classList.add("over"); });
      dropzone.addEventListener("dragleave", function () { dropzone.classList.remove("over"); });
      dropzone.addEventListener("drop", function (e) {
        e.preventDefault();
        dropzone.classList.remove("over");
        Array.prototype.forEach.call(e.dataTransfer.files, Knowledge.upload);
      });
      el("docInput").addEventListener("change", function () {
        Array.prototype.forEach.call(this.files, Knowledge.upload);
        this.value = "";
      });
      Knowledge.loadDocuments(id);
    },

    loadDocuments: function (kbId) {
      return api("/api/documents?knowledge_base_id=" + encodeURIComponent(kbId)).then(function (data) {
        var docs = data.data || [];
        el("docList").innerHTML = docs.length ? docs.map(function (d) {
          var state = d.status === "ready" ? "" :
            ' <span class="chip' + (d.status === "failed" ? " warn" : "") + '">' + esc(d.status) + "</span>";
          return '<div class="row" data-id="' + esc(d.id) + '"><div class="row-title">' + esc(d.display_name) + state + "</div>" +
            '<div class="row-sub">' + esc(d.failure_reason || d.mime_type || "") + " · " + relativeTime(d.imported_at) + "</div>" +
            '<div class="row-actions"><button class="icon-btn destructive" data-act="delete" aria-label="Delete" title="Delete">' + icon("trash") + "</button></div></div>";
        }).join("") : '<div class="empty-note">No documents yet.</div>';

        Array.prototype.forEach.call(el("docList").querySelectorAll(".row"), function (row) {
          row.addEventListener("click", function (e) {
            if (e.target.getAttribute && e.target.getAttribute("data-act") === "delete") {
              e.stopPropagation();
              confirmThen("Delete this document?", function () {
                return api("/api/documents/delete", { id: row.getAttribute("data-id") })
                  .then(function () { return Knowledge.loadDocuments(kbId); });
              });
            }
          });
        });

        // Importing runs asynchronously on the phone (extract → chunk → embed), so a document that
        // is still working is polled until it settles rather than needing a manual refresh.
        if (docs.some(function (d) { return d.status !== "ready" && d.status !== "failed"; })) {
          setTimeout(function () {
            if (Knowledge.activeId === kbId && current === "knowledge") Knowledge.loadDocuments(kbId);
          }, 2500);
        }
      }).catch(fail);
    },

    upload: function (file) {
      var reader = new FileReader();
      reader.onload = function () {
        var dataUrl = String(reader.result);
        toast("Uploading " + file.name + "…");
        api("/api/documents", {
          knowledge_base_id: Knowledge.activeId,
          name: file.name,
          mime_type: file.type || "application/octet-stream",
          data: dataUrl.slice(dataUrl.indexOf(",") + 1)
        }, { timeoutMs: 120000 }).then(function (res) {
          toast(res.duplicate ? file.name + " was already imported" : file.name + " imported");
          return Knowledge.loadDocuments(Knowledge.activeId);
        }).catch(fail);
      };
      reader.readAsDataURL(file);
    }
  };
  el("kbSearch").addEventListener("input", function () { Knowledge.renderList(); });
  el("newKbBtn").addEventListener("click", function () { Knowledge.edit(null); });
  Knowledge.edit = function (kb) {
    openModal(kb ? "Edit knowledge base" : "New knowledge base", [
      { key: "name", label: "Name", value: (kb && kb.name) || "" },
      { key: "description", label: "Description", value: (kb && kb.description) || "" }
    ], function (values) {
      var payload = Object.assign({}, values);
      if (kb) payload.id = kb.id;
      return api("/api/knowledge-bases", payload).then(function (saved) {
        Knowledge.activeId = saved.id;
        return Knowledge.load();
      });
    });
  };

  // ================================================================ TOOLS

  var Tools = {
    tools: [], appToolsOn: false, writesOn: false, activeName: null,

    load: function () {
      if (!Tools.tools.length) el("toolList").innerHTML = skeletonRows(7);
      return tracked(api("/api/tools")).then(function (data) {
        Tools.tools = data.data || [];
        Tools.appToolsOn = !!data.app_tools_enabled;
        Tools.writesOn = !!data.write_tools_allowed;
        Tools.renderList();
        if (Tools.activeName) Tools.select(Tools.activeName);
        else el("toolDetail").innerHTML = Tools.appToolsOn
          ? '<div class="empty-note">Pick a tool to run it on the phone.</div>'
          : '<div class="card"><h3>Tools are off for API clients</h3><p>Turn on “Let API clients use this device’s tools” in the app under Settings → Local API server to run tools from here.</p></div>';
      }).catch(function (err) {
        showLoadError("toolList", "Tools are unavailable", err, Tools.load);
        fail(err);
      });
    },

    renderList: function () {
      var q = el("toolSearch").value.trim().toLowerCase();
      var shown = Tools.tools.filter(function (t) {
        return !q || t.name.toLowerCase().indexOf(q) >= 0 || (t.description || "").toLowerCase().indexOf(q) >= 0;
      });
      el("toolList").innerHTML = shown.length ? shown.map(function (t) {
        return '<div class="row' + (t.name === Tools.activeName ? " active" : "") + '" data-name="' + esc(t.name) + '">' +
          '<div class="row-title">' + esc(t.name) + (t.risk !== "READ_ONLY" ? ' <span class="chip warn">write</span>' : "") + "</div>" +
          '<div class="row-sub">' + esc(t.description || "") + "</div></div>";
      }).join("") : '<div class="empty-note">No tools matched.</div>';
      Array.prototype.forEach.call(el("toolList").querySelectorAll(".row"), function (row) {
        row.addEventListener("click", function () { Tools.select(row.getAttribute("data-name")); closeLists(); });
      });
    },

    select: function (name) {
      Tools.activeName = name;
      Tools.renderList();
      var tool = Tools.tools.find(function (t) { return t.name === name; });
      if (!tool) return;
      var blocked = !Tools.appToolsOn || (tool.risk !== "READ_ONLY" && !Tools.writesOn);
      el("toolDetail").innerHTML =
        "<h2>" + esc(tool.name) + "</h2><p>" + esc(tool.description || "") + "</p>" +
        '<p class="row-sub">' + esc(tool.category || "") + " · " + esc(tool.risk.toLowerCase().replace(/_/g, " ")) + "</p>" +
        (blocked ? '<div class="card"><p>This tool can\'t be run from the browser with the current settings. ' +
          "Enable API-client tools" + (tool.risk !== "READ_ONLY" ? " and write access" : "") +
          " in the app under Settings → Local API server.</p></div>" : "") +
        '<div class="card"><h3>Parameters</h3>' +
        (tool.parameters && tool.parameters.length
          ? tool.parameters.map(function (p) {
              return '<label class="field"><span>' + esc(p) + '</span><input type="text" id="tp_' + esc(p) + '"></label>';
            }).join("")
          : '<p class="row-sub">This tool takes no parameters.</p>') +
        '<button class="primary" type="button" id="runToolBtn"' + (blocked ? " disabled" : "") + ">Run</button></div>" +
        '<div id="toolOutput"></div>';

      var runBtn = el("runToolBtn");
      if (runBtn) runBtn.addEventListener("click", function () {
        var payload = {};
        (tool.parameters || []).forEach(function (p) {
          var input = el("tp_" + p);
          if (input && input.value.trim()) payload[p] = input.value.trim();
        });
        runBtn.disabled = true;
        el("toolOutput").innerHTML = '<div class="empty-note">Running…</div>';
        api("/api/tools/run", { tool: tool.name, params: payload }).then(function (res) {
          el("toolOutput").innerHTML = '<div class="card"><h3>' + (res.success ? "Result" : "Failed") +
            '</h3><div id="toolSummary"></div></div>';
          R.renderMarkdown(el("toolSummary"), res.summary || "");
        }).catch(function (err) {
          el("toolOutput").innerHTML = '<div class="card"><p style="color:var(--danger)">' + esc(err.message) + "</p></div>";
        }).then(function () { runBtn.disabled = false; });
      });
    },

    history: function () {
      api("/api/tool-runs").then(function (data) {
        var runs = data.tool_runs || [];
        el("toolDetail").innerHTML = "<h2>Tool history</h2>" + (runs.length ? runs.map(function (r) {
          return '<div class="card"><h3>' + esc(r.name) + ' <span class="chip">' + esc(r.state.toLowerCase()) + "</span></h3>" +
            '<p class="row-sub">' + relativeTime(r.updated_at) + (r.model_name ? " · " + esc(r.model_name) : "") + "</p>" +
            "<p>" + esc((r.output || r.error || r.input || "").slice(0, 600)) + "</p></div>";
        }).join("") : '<div class="empty-note">No tool runs recorded yet.</div>');
        Tools.activeName = null;
        Tools.renderList();
      }).catch(fail);
    }
  };
  el("toolSearch").addEventListener("input", function () { Tools.renderList(); });
  el("toolHistoryBtn").addEventListener("click", function () { Tools.history(); });

  // ================================================================ MODELS

  var Models = {
    ttlTimer: null,

    load: function () {
      if (!el("modelsPane").children.length) el("modelsPane").innerHTML = skeletonCards(3);
      return tracked(api("/api/models")).then(function (data) {
        Models.render(data);
      }).catch(function (err) {
        showLoadError("modelsPane", "Models are unavailable", err, Models.load);
        fail(err);
      });
    },

    summaryHtml: function (data) {
      if (!data) return '<div class="empty-note">Model status unavailable.</div>';
      var roles = data.roles || {};
      return ["generation", "embedding"].map(function (role) {
        var info = roles[role] || {};
        var models = data.models || [];
        var loaded = models.find(function (m) { return m.id === info.loaded_model_id; });
        var fallback = models.find(function (m) { return m.id === info.default_model_id; });
        var state;
        if (info.phase === "LOADING") state = '<span class="chip warn">loading…</span>';
        else if (loaded) state = '<span class="chip on">loaded</span>';
        else state = '<span class="chip">not loaded</span>';
        return '<div class="switch-row"><span><b>' + role.charAt(0).toUpperCase() + role.slice(1) + "</b> · " +
          esc((loaded || fallback || {}).name || "none installed") +
          (!loaded && fallback ? ' <span class="row-sub">(default, loads on first request)</span>' : "") +
          "</span>" + state + "</div>";
      }).join("");
    },

    render: function (data) {
      var roles = data.roles || {};
      var ttl = data.ttl_seconds || 0;
      var html = '<div class="card"><h3>Residency</h3>' + Models.summaryHtml(data) +
        '<p class="row-sub" style="margin-top:10px">' +
        (ttl > 0
          ? "A model loaded by an API request unloads after " + ttl + "s idle. Change this in the app under Settings → Models."
          : "Idle unloading is off — a model loaded by an API request stays resident.") +
        "</p>";
      var genTtl = (roles.generation || {}).ttl_expires_at;
      if (genTtl) html += '<p class="row-sub" id="ttlCountdown"></p>';
      html += "</div>";

      html += '<div class="card"><h3>Installed models</h3>';
      var models = data.models || [];
      html += models.length ? models.map(function (m) {
        var caps = m.capabilities || {};
        var badges = [];
        if (caps.vision) badges.push("vision");
        if (caps.audio) badges.push("audio");
        if (caps.tools) badges.push("tools");
        if (caps.thinking) badges.push("thinking");
        var roleInfo = roles[m.role.toLowerCase()] || {};
        var isLoaded = roleInfo.loaded_model_id === m.id;
        return '<div class="row" style="cursor:default"><div class="row-title">' + esc(m.name) +
          (m.is_default ? ' <span class="chip on">default</span>' : "") +
          (isLoaded ? ' <span class="chip on">loaded</span>' : "") + "</div>" +
          '<div class="row-sub">' + esc(m.role.toLowerCase()) + " · " + esc(m.engine) + " · " + bytes(m.size_bytes) +
          (m.context_tokens ? " · " + m.context_tokens + " ctx" : "") +
          (badges.length ? " · " + badges.join(", ") : "") + "</div>" +
          '<div class="modal-actions" style="margin-top:8px">' +
            (isLoaded ? "" : '<button class="secondary" type="button" data-load="' + esc(m.id) + '">Load</button>') +
            (m.is_default ? "" : '<button class="ghost" type="button" data-default="' + esc(m.id) + '">Make default</button>') +
            (isLoaded ? '<button class="ghost" type="button" data-unload="' + esc(m.role) + '">Unload</button>' : "") +
          "</div></div>";
      }).join("") : '<div class="empty-note">No models installed. Import one in the app.</div>';
      html += "</div>";
      el("modelsPane").innerHTML = html;

      function wire(attr, handler) {
        Array.prototype.forEach.call(el("modelsPane").querySelectorAll("[" + attr + "]"), function (b) {
          b.addEventListener("click", function () {
            b.disabled = true;
            handler(b.getAttribute(attr)).then(function (fresh) { Models.render(fresh); }).catch(function (err) {
              b.disabled = false;
              fail(err);
            });
          });
        });
      }
      wire("data-load", function (id) { toast("Loading…"); return api("/api/models/load", { id: id }); });
      wire("data-unload", function (role) { return api("/api/models/unload", { role: role }); });
      wire("data-default", function (id) { return api("/api/models/default", { id: id }); });

      clearInterval(Models.ttlTimer);
      if (genTtl) {
        Models.ttlTimer = setInterval(function () {
          var node = el("ttlCountdown");
          if (!node) { clearInterval(Models.ttlTimer); return; }
          var left = Math.max(0, Math.round((genTtl - Date.now()) / 1000));
          node.textContent = left > 0 ? "Unloads in " + left + "s unless another request arrives." : "Unloading…";
        }, 1000);
      }
    }
  };

  // ================================================================ RECYCLE BIN

  /**
   * Everything soft-deleted, across all nine types, with restore and permanent-delete.
   *
   * Worth its own destination rather than a Settings corner: every delete button in this app is a
   * soft delete, and without somewhere to see that, they all read as irreversible. Purge is the
   * one genuinely irreversible action here, so it confirms separately and says so plainly.
   */
  var Recycle = {
    items: [], filter: "all",

    load: function () {
      el("recyclePane").innerHTML = loadingBlock("Loading the recycle bin…");
      return tracked(api("/api/recycle-bin")).then(function (data) {
        Recycle.items = data.items || [];
        Recycle.render();
      }).catch(function (err) {
        el("recyclePane").innerHTML = '<div class="card"><p style="color:var(--danger)">' + esc(err.message) + "</p></div>";
        fail(err);
      });
    },

    render: function () {
      var counts = {};
      Recycle.items.forEach(function (item) { counts[item.type] = (counts[item.type] || 0) + 1; });
      var kinds = Object.keys(counts).sort();
      el("recycleTabs").innerHTML =
        '<button type="button" data-filter="all" class="' + (Recycle.filter === "all" ? "active" : "") + '">All (' + Recycle.items.length + ")</button>" +
        kinds.map(function (kind) {
          return '<button type="button" data-filter="' + esc(kind) + '" class="' + (Recycle.filter === kind ? "active" : "") + '">' +
            esc(labelFor(kind)) + " (" + counts[kind] + ")</button>";
        }).join("");
      Array.prototype.forEach.call(el("recycleTabs").querySelectorAll("button"), function (button) {
        button.addEventListener("click", function () {
          Recycle.filter = button.getAttribute("data-filter");
          Recycle.render();
        });
      });

      var shown = Recycle.items.filter(function (item) {
        return Recycle.filter === "all" || item.type === Recycle.filter;
      });
      if (!shown.length) {
        el("recyclePane").innerHTML =
          '<div class="card"><h3>Nothing here</h3><p class="row-sub">Deleted chats, notes, memories, personas, ' +
          "prompts, saved outputs, projects, folders and documents land here first, so a delete is always undoable.</p></div>";
        return;
      }
      el("recyclePane").innerHTML =
        '<div class="card"><h3>' + shown.length + " item" + (shown.length === 1 ? "" : "s") + "</h3>" +
        shown.map(function (item) {
          return '<div class="row" style="cursor:default" data-type="' + esc(item.type) + '" data-id="' + esc(item.id) + '">' +
            '<div class="row-title">' + esc(item.title) + "</div>" +
            '<div class="row-sub">' + esc(labelFor(item.type)) +
            (item.deleted_at ? " · deleted " + relativeTime(item.deleted_at) : "") + "</div>" +
            '<div class="modal-actions" style="margin-top:8px">' +
              '<button class="secondary" type="button" data-act="restore">Restore</button>' +
              '<button class="ghost" type="button" data-act="purge" style="color:var(--danger)">Delete forever</button>' +
            "</div></div>";
        }).join("") + "</div>";

      Array.prototype.forEach.call(el("recyclePane").querySelectorAll(".row"), function (row) {
        var payload = { type: row.getAttribute("data-type"), id: row.getAttribute("data-id") };
        var title = row.querySelector(".row-title").textContent;
        row.addEventListener("click", function (e) {
          var act = e.target.getAttribute && e.target.getAttribute("data-act");
          if (act === "restore") {
            withBusy(e.target, api("/api/recycle-bin/restore", payload))
              .then(function () { toast("Restored"); return Recycle.load(); })
              .catch(fail);
          }
          if (act === "purge") {
            confirmThen(
              "“" + title + "” will be deleted permanently. This cannot be undone.",
              function () {
                return api("/api/recycle-bin/purge", payload).then(function () {
                  toast("Deleted permanently");
                  return Recycle.load();
                });
              },
              { title: "Delete forever?", confirmLabel: "Delete forever" }
            );
          }
        });
      });
    }
  };

  function labelFor(type) {
    return {
      chat: "Chat", note: "Note", memory: "Memory", persona: "Persona", template: "Prompt template",
      "saved-output": "Saved output", project: "Project", folder: "Folder", document: "Document"
    }[type] || type;
  }

  // ================================================================ boot

  autoGrow();
  // No chat is open at boot, so the settings panel shows its "open a chat" note rather than a set
  // of controls that would write to nothing.
  el("optsBody").style.display = "none";
  el("optsNoChat").style.display = "";
  go((location.hash || "#home").slice(1));

  // The chat bar's two selects use the pill shape the bar was designed around.
  el("modelSelect").dataset.pill = "1";
  el("thinkingSelect").dataset.pill = "1";
  upgradeSelectsIn(document);

  // A cheap liveness check so the header reflects reality before the user sends anything.
  api("/health").then(function () { setStatus("ok"); }).catch(function (err) {
    setStatus(/API key/.test(err.message) ? "auth" : "err");
  });

  /**
   * Device RAM in the header, refreshed on a slow cadence.
   *
   * This is a phone serving the page, not a server — how much memory is left is the single most
   * useful signal for why a model failed to load or a reply crawled, and it is invisible from the
   * browser otherwise. Polling pauses while the tab is hidden so a backgrounded tab isn't waking
   * the device for a number nobody is reading.
   */
  function refreshSystem() {
    if (document.hidden) return;
    api("/api/system").then(function (data) {
      var memory = (data && data.memory) || {};
      if (!memory.total_mb) { el("ramChip").hidden = true; return; }
      el("ramChip").hidden = false;
      el("ramChip").className = "chip" + (memory.low ? " warn" : "");
      el("ramChip").textContent = memory.available_mb + " / " + memory.total_mb + " MB free";
      el("ramChip").title = "Free memory on the device serving this page";
    }).catch(function () { el("ramChip").hidden = true; });
  }
  refreshSystem();
  setInterval(refreshSystem, 15000);
  document.addEventListener("visibilitychange", function () { if (!document.hidden) refreshSystem(); });
})();
