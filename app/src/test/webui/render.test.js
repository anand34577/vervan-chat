// Regression tests for the web UI's Markdown/LaTeX renderer (app/src/main/assets/webui/render.js).
// Run with: node app/src/test/webui/render.test.js   (no dependencies)
"use strict";
global.window = {};
require("../../main/assets/webui/render.js");
const R = window.VervanRender;

const cases = [
  // Super/subscripts attach to the preceding atom only, not the whole expression.
  ["$x+y^2$", "<mi>x</mi><mo>+</mo><msup><mi>y</mi><mi>2</mi></msup>"],
  ["$a_1 + b_2$", "<mo>+</mo><msub><mi>b</mi><mi>2</mi></msub>"],
  ["$\\frac{a}{b}^2$", "<msup><mfrac>"],
  ["$\\left( x \\right.$", "<mo>(</mo><mi>x</mi></mrow>"],
  ["$x^$", "<msup><mi>x</mi><mi></mi></msup>"],
  // Code spans are literal: no emphasis inside, no overlapping tags.
  ["use `a*b*c` here", "<code>a*b*c</code>"],
  ["**bold** and `x**y`", "<strong>bold</strong> and <code>x**y</code>"],
  ["cost `$5 and $6` ok", "<code>$5 and $6</code>"],
  // Untrusted model text never becomes markup or a script URL.
  ["<img src=x onerror=alert(1)>", "&lt;img src=x onerror=alert(1)&gt;"],
  ["[x](javascript:alert(1))", "[x](javascript:alert(1))"],
  ['[x](https://a.test/"onmouseover=1)', 'href="https://a.test/&quot;onmouseover=1"'],
];

let failed = 0;
for (const [input, expected] of cases) {
  const out = R.renderMarkdownText(input);
  const ok = out.includes(expected);
  if (!ok) failed++;
  console.log((ok ? "PASS " : "FAIL ") + JSON.stringify(input) + (ok ? "" : "\n     got: " + out));
}
console.log(`${cases.length - failed}/${cases.length} passed`);
process.exit(failed ? 1 : 0);
