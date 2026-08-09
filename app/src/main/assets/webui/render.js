/*
 * Markdown + LaTeX + Mermaid rendering for the web app.
 *
 * Extracted verbatim from the previous single-file web UI so the rewrite around it changed the
 * application shell without disturbing output fidelity: the same headings, tables, fenced code,
 * ```mermaid diagrams and $$…$$ math a model produces render identically in the browser and in the
 * native app's MarkdownLite. Exposed as window.VervanRender rather than left inline because the
 * chat view, the note editor, the tool-run viewer and the document preview all need it.
 */
window.VervanRender = (function () {
  "use strict";

  function escapeHtml(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  // ---- Mermaid ------------------------------------------------------------
  var mermaidLoadPromise = null;
  function ensureMermaid() {
    if (window.mermaid) return Promise.resolve(window.mermaid);
    if (mermaidLoadPromise) return mermaidLoadPromise;
    mermaidLoadPromise = new Promise(function (resolve, reject) {
      var script = document.createElement("script");
      script.src = "/webui-assets/mermaid.min.js";
      script.onload = function () { resolve(window.mermaid); };
      script.onerror = function () { reject(new Error("Could not load the diagram renderer.")); };
      document.head.appendChild(script);
    });
    return mermaidLoadPromise;
  }
  var mermaidSeq = 0;
  function renderMermaidInto(container, source, onError) {
    var style = getComputedStyle(document.documentElement);
    var cssVar = function (name) { return style.getPropertyValue(name).trim(); };
    ensureMermaid().then(function (mermaid) {
      mermaid.initialize({
        startOnLoad: false, securityLevel: "strict", theme: "base",
        themeVariables: {
          background: cssVar("--surface"), mainBkg: cssVar("--surface"),
          primaryColor: cssVar("--accent-soft"), primaryTextColor: cssVar("--text"), primaryBorderColor: cssVar("--accent"),
          secondaryColor: cssVar("--surface-alt"), secondaryTextColor: cssVar("--text"), secondaryBorderColor: cssVar("--border"),
          tertiaryColor: cssVar("--surface"), tertiaryTextColor: cssVar("--text"), tertiaryBorderColor: cssVar("--border"),
          lineColor: cssVar("--border-strong"), textColor: cssVar("--text"),
          noteBkgColor: cssVar("--surface-alt"), noteTextColor: cssVar("--text")
        },
        flowchart: { useMaxWidth: true, htmlLabels: false },
        sequence: { useMaxWidth: true },
        suppressErrorRendering: true
      });
      var id = "mmd-" + (++mermaidSeq);
      return mermaid.parse(source).then(function () { return mermaid.render(id, source); });
    }).then(function (result) {
      container.innerHTML = result.svg;
      if (result.bindFunctions) result.bindFunctions(container);
    }).catch(function (err) {
      container.innerHTML = '<div class="mermaid-error">Diagram could not be rendered: ' +
        escapeHtml((err && err.message) || String(err)) + "</div>";
      if (onError) onError(err);
    });
  }

  /**
   * A mermaid block as a card with a Diagram/Code switch and a copy button.
   *
   * The source matters as much as the picture: it is what the user edits, pastes elsewhere, or
   * reads when the diagram fails to parse — which is also why a render error leaves the card on
   * the Code tab rather than showing an error where the source used to be.
   */
  function mermaidCard(source) {
    var wrap = document.createElement("div");
    wrap.className = "mermaid-wrap";

    var head = document.createElement("div");
    head.className = "mermaid-head";
    head.innerHTML = '<span>Diagram</span><span class="grow"></span>' +
      '<span class="seg"><button type="button" data-view="diagram" class="on">Diagram</button>' +
      '<button type="button" data-view="code">Code</button></span>';
    var copyBtn = document.createElement("button");
    copyBtn.className = "icon-btn"; copyBtn.type = "button"; copyBtn.textContent = "Copy";
    copyBtn.addEventListener("click", function () {
      if (!navigator.clipboard) return;
      navigator.clipboard.writeText(source).then(function () {
        copyBtn.textContent = "Copied";
        setTimeout(function () { copyBtn.textContent = "Copy"; }, 1100);
      });
    });
    head.appendChild(copyBtn);

    var diagram = document.createElement("div");
    diagram.className = "mermaid-block";
    diagram.innerHTML = '<div class="loading-block"><span class="spinner"></span>Rendering diagram…</div>';

    var code = document.createElement("pre");
    code.className = "mermaid-source";
    code.hidden = true;
    var codeInner = document.createElement("code");
    codeInner.textContent = source;
    code.appendChild(codeInner);

    head.addEventListener("click", function (e) {
      var view = e.target.getAttribute && e.target.getAttribute("data-view");
      if (!view) return;
      var showCode = view === "code";
      diagram.hidden = showCode;
      code.hidden = !showCode;
      Array.prototype.forEach.call(head.querySelectorAll("[data-view]"), function (b) {
        b.classList.toggle("on", b.getAttribute("data-view") === view);
      });
    });

    wrap.appendChild(head);
    wrap.appendChild(diagram);
    wrap.appendChild(code);
    renderMermaidInto(diagram, source, function onError() {
      diagram.hidden = true;
      code.hidden = false;
      Array.prototype.forEach.call(head.querySelectorAll("[data-view]"), function (b) {
        b.classList.toggle("on", b.getAttribute("data-view") === "code");
      });
    });
    return wrap;
  }

  // ---- LaTeX → MathML (no external library — the browser's native <math> support) ----
  var GREEK = {
    alpha: "α", beta: "β", gamma: "γ", delta: "δ", epsilon: "ε", zeta: "ζ", eta: "η", theta: "θ",
    iota: "ι", kappa: "κ", lambda: "λ", mu: "μ", nu: "ν", xi: "ξ", pi: "π", rho: "ρ", sigma: "σ",
    tau: "τ", upsilon: "υ", phi: "φ", chi: "χ", psi: "ψ", omega: "ω",
    Gamma: "Γ", Delta: "Δ", Theta: "Θ", Lambda: "Λ", Xi: "Ξ", Pi: "Π", Sigma: "Σ", Upsilon: "Υ", Phi: "Φ", Psi: "Ψ", Omega: "Ω",
    infty: "∞", cdot: "⋅", times: "×", div: "÷", pm: "±", mp: "∓", leq: "≤", geq: "≥", neq: "≠",
    approx: "≈", equiv: "≡", sim: "∼", propto: "∝", partial: "∂", nabla: "∇", forall: "∀", exists: "∃",
    in: "∈", notin: "∉", subset: "⊂", subseteq: "⊆", cup: "∪", cap: "∩", emptyset: "∅", rightarrow: "→",
    leftarrow: "←", Rightarrow: "⇒", to: "→", cdots: "⋯", ldots: "…", sum: "∑", prod: "∏", int: "∫"
  };
  function latexTokenToMathML(src) {
    var i = 0;
    function readGroup() {
      if (src[i] === "{") {
        var depth = 0, start = i;
        do { if (src[i] === "{") depth++; else if (src[i] === "}") depth--; i++; } while (depth > 0 && i < src.length);
        return parseSeq(src.slice(start + 1, i - 1));
      }
      if (src[i] === "\\") { var m = /^\\[a-zA-Z]+/.exec(src.slice(i)); if (m) { i += m[0].length; return atomOrCommand(m[0].slice(1)); } }
      var ch = src[i]; i++;
      return "<mi>" + escapeHtml(ch) + "</mi>";
    }
    function atomOrCommand(name) {
      if (GREEK[name]) return "<mi>" + GREEK[name] + "</mi>";
      if (name === "frac") { var a = readGroup(); var b = readGroup(); return "<mfrac>" + a + b + "</mfrac>"; }
      if (name === "sqrt") { var g = readGroup(); return "<msqrt>" + g + "</msqrt>"; }
      if (name === "text") { var t = readGroup(); return "<mtext>" + t.replace(/<\/?mi>|<\/?mrow>/g, "") + "</mtext>"; }
      if (name === "left" || name === "right") { var d = src[i]; i++; return d && d !== "." ? "<mo>" + escapeHtml(d) + "</mo>" : ""; }
      if (name === "sum" || name === "prod" || name === "int") {
        var op = name === "sum" ? "∑" : (name === "prod" ? "∏" : "∫");
        return "<mo>" + op + "</mo>";
      }
      return "<mi>" + escapeHtml(name) + "</mi>";
    }
    function parseSeq(text) {
      var save = [i, src]; src = text; i = 0;
      var out = "";
      function popLast() {
        var m2 = /(<m(?:i|n|o|sup|sub|frac|sqrt|text)[^]*>[^]*<\/m(?:i|n|o|sup|sub|frac|sqrt|text)>|<m[a-z]+\/>)$/.exec(out);
        if (m2) { out = out.slice(0, out.length - m2[0].length); return m2[0]; }
        return "<mi></mi>";
      }
      while (i < src.length) {
        var ch = src[i];
        if (ch === " ") { i++; continue; }
        if (ch === "^" || ch === "_") {
          i++;
          var base = out.length ? popLast() : "<mi></mi>";
          var sup = readGroup();
          out += ch === "^" ? "<msup>" + base + sup + "</msup>" : "<msub>" + base + sup + "</msub>";
          continue;
        }
        if (ch === "{") { out += readGroup(); continue; }
        if (ch === "\\") { var m = /^\\[a-zA-Z]+/.exec(src.slice(i)); if (m) { i += m[0].length; out += atomOrCommand(m[0].slice(1)); continue; } i++; continue; }
        if (/[+\-=<>]/.test(ch)) { out += "<mo>" + escapeHtml(ch) + "</mo>"; i++; continue; }
        if (/[0-9.]/.test(ch)) { var num = ""; while (i < src.length && /[0-9.]/.test(src[i])) { num += src[i]; i++; } out += "<mn>" + num + "</mn>"; continue; }
        out += "<mi>" + escapeHtml(ch) + "</mi>"; i++;
      }
      var res = out;
      i = save[0]; src = save[1];
      return "<mrow>" + res + "</mrow>";
    }
    return parseSeq(src);
  }
  function renderLatex(source, displayBlock) {
    try {
      var body = latexTokenToMathML(source.trim());
      return '<math xmlns="http://www.w3.org/1998/Math/MathML"' + (displayBlock ? ' display="block"' : "") + ">" + body + "</math>";
    } catch (e) {
      return "<code>" + escapeHtml(source) + "</code>";
    }
  }

  // ---- Markdown -----------------------------------------------------------
  function renderMarkdown(container, text) {
    container.innerHTML = "";
    var normalized = String(text == null ? "" : text)
      .replace(/\\\[([\s\S]*?)\\\]/g, function (m, g) { return "$$" + g + "$$"; })
      .replace(/\\\(([\s\S]*?)\\\)/g, function (m, g) { return "$" + g + "$"; });

    var blocks = [];
    var codeRe = /```([a-zA-Z0-9_+-]*)\n?([\s\S]*?)```/g;
    var lastIndex = 0, m;
    while ((m = codeRe.exec(normalized))) {
      if (m.index > lastIndex) blocks.push({ type: "text", value: normalized.slice(lastIndex, m.index) });
      blocks.push({ type: "code", lang: m[1] || "", value: m[2] });
      lastIndex = codeRe.lastIndex;
    }
    if (lastIndex < normalized.length) blocks.push({ type: "text", value: normalized.slice(lastIndex) });

    blocks.forEach(function (block) {
      if (block.type === "code") {
        if (block.lang.toLowerCase() === "mermaid") {
          container.appendChild(mermaidCard(block.value.trim()));
        } else {
          var cb = document.createElement("div");
          cb.className = "code-block";
          var head = document.createElement("div");
          head.className = "code-block-head";
          head.innerHTML = "<span>" + escapeHtml(block.lang || "text") + "</span>";
          var copyBtn = document.createElement("button");
          copyBtn.className = "icon-btn"; copyBtn.type = "button"; copyBtn.textContent = "Copy";
          copyBtn.addEventListener("click", function () {
            if (navigator.clipboard) navigator.clipboard.writeText(block.value).then(function () {
              copyBtn.textContent = "Copied"; setTimeout(function () { copyBtn.textContent = "Copy"; }, 1100);
            });
          });
          head.appendChild(copyBtn);
          cb.appendChild(head);
          var pre = document.createElement("pre");
          var code = document.createElement("code");
          code.textContent = block.value.replace(/\n$/, "");
          pre.appendChild(code);
          cb.appendChild(pre);
          container.appendChild(cb);
        }
      } else {
        var div = document.createElement("div");
        div.innerHTML = renderMarkdownText(block.value);
        while (div.firstChild) container.appendChild(div.firstChild);
      }
    });
  }

  function renderMarkdownText(md) {
    // Protect LaTeX spans from the rest of the markdown pass, then swap in MathML at the end.
    var mathStore = [];
    md = md.replace(/\$\$([\s\S]+?)\$\$/g, function (m, g) { mathStore.push(renderLatex(g, true)); return " MATH" + (mathStore.length - 1) + " "; });
    md = md.replace(/\$([^\n$]+?)\$/g, function (m, g) { mathStore.push(renderLatex(g, false)); return " MATH" + (mathStore.length - 1) + " "; });

    var lines = md.split("\n");
    var html = "";
    var i = 0;
    function inline(text) {
      text = escapeHtml(text);
      text = text.replace(/ MATH(\d+) /g, function (m, idx) { return mathStore[parseInt(idx, 10)]; });
      text = text.replace(/`([^`]+)`/g, "<code>$1</code>");
      text = text.replace(/\*\*([^*]+)\*\*|__([^_]+)__/g, function (m, a, b) { return "<strong>" + (a || b) + "</strong>"; });
      text = text.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, "$1<em>$2</em>");
      text = text.replace(/~~([^~]+)~~/g, "<del>$1</del>");
      text = text.replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
      return text;
    }
    while (i < lines.length) {
      var line = lines[i];
      if (/^\s*$/.test(line)) { i++; continue; }
      var h = /^(#{1,6})\s+(.*)$/.exec(line);
      if (h) { html += "<h" + h[1].length + ">" + inline(h[2]) + "</h" + h[1].length + ">"; i++; continue; }
      if (/^\s*(-{3,}|\*{3,}|_{3,})\s*$/.test(line)) { html += "<hr>"; i++; continue; }
      if (/^\s*>/.test(line)) {
        var quote = [];
        while (i < lines.length && /^\s*>/.test(lines[i])) { quote.push(lines[i].replace(/^\s*>\s?/, "")); i++; }
        html += "<blockquote>" + renderMarkdownText(quote.join("\n")) + "</blockquote>";
        continue;
      }
      if (/^\s*\|.*\|\s*$/.test(line) && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1])) {
        var headCells = line.trim().replace(/^\||\|$/g, "").split("|").map(function (c) { return c.trim(); });
        i += 2;
        var rows = [];
        while (i < lines.length && /^\s*\|.*\|\s*$/.test(lines[i])) {
          rows.push(lines[i].trim().replace(/^\||\|$/g, "").split("|").map(function (c) { return c.trim(); }));
          i++;
        }
        html += '<div class="table-scroll"><table><thead><tr>' +
          headCells.map(function (c) { return "<th>" + inline(c) + "</th>"; }).join("") + "</tr></thead><tbody>" +
          rows.map(function (r) { return "<tr>" + r.map(function (c) { return "<td>" + inline(c) + "</td>"; }).join("") + "</tr>"; }).join("") +
          "</tbody></table></div>";
        continue;
      }
      var ol = /^\s*\d+[.)]\s+/.test(line);
      var ul = /^\s*[-*+]\s+/.test(line);
      if (ol || ul) {
        var tag = ol ? "ol" : "ul";
        var items = [];
        while (i < lines.length && (ol ? /^\s*\d+[.)]\s+/.test(lines[i]) : /^\s*[-*+]\s+/.test(lines[i]))) {
          items.push(lines[i].replace(ol ? /^\s*\d+[.)]\s+/ : /^\s*[-*+]\s+/, ""));
          i++;
        }
        html += "<" + tag + ">" + items.map(function (it) { return "<li>" + inline(it) + "</li>"; }).join("") + "</" + tag + ">";
        continue;
      }
      var para = [];
      while (i < lines.length && !/^\s*$/.test(lines[i]) && !/^(#{1,6})\s+/.test(lines[i]) &&
             !/^\s*[-*+]\s+/.test(lines[i]) && !/^\s*\d+[.)]\s+/.test(lines[i]) && !/^\s*>/.test(lines[i])) {
        para.push(lines[i]); i++;
      }
      html += "<p>" + inline(para.join(" ")) + "</p>";
    }
    return html;
  }

  return {
    escapeHtml: escapeHtml,
    renderMarkdown: renderMarkdown,
    renderMarkdownText: renderMarkdownText
  };
})();
