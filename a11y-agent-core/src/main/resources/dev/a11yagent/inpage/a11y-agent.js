/*
 * a11y-agent in-page rules bundle.
 *
 * Plain browser JavaScript, no dependencies. Loaded by the Java engine through page.evaluate() and
 * designed so the very same file can run as a browser-extension content script.
 *
 * Every rule returns raw findings: { outcome, selector, html, rect, message, data }.
 * Outcomes: passed | failed | inapplicable | cantTell | needsReview.
 * WCAG mapping and severities live on the Java side (dev.a11yagent.core.rules.Rules).
 */
(function () {
  if (window.__a11yAgent) return;

  var A = { version: '0.1.0' };

  /* ------------------------------------------------------------------ utils */

  function esc(s) {
    return (window.CSS && CSS.escape) ? CSS.escape(s) : String(s).replace(/([^\w-])/g, '\\$1');
  }

  function cssPath(el) {
    if (!el || el.nodeType !== 1) return null;
    if (el === document.documentElement) return 'html';
    if (el === document.body) return 'body';
    if (el.id && document.querySelectorAll('#' + esc(el.id)).length === 1) return '#' + esc(el.id);
    var parts = [];
    var node = el;
    while (node && node.nodeType === 1 && node !== document.body && node !== document.documentElement) {
      var tag = node.nodeName.toLowerCase();
      var parent = node.parentElement;
      if (!parent) break;
      var sameTag = Array.prototype.filter.call(parent.children, function (c) { return c.nodeName === node.nodeName; });
      var part = tag;
      if (sameTag.length > 1) part += ':nth-of-type(' + (sameTag.indexOf(node) + 1) + ')';
      parts.unshift(part);
      if (parent.id && document.querySelectorAll('#' + esc(parent.id)).length === 1) {
        parts.unshift('#' + esc(parent.id));
        return parts.join(' > ');
      }
      node = parent;
    }
    parts.unshift(node === document.documentElement ? 'html' : 'body');
    return parts.join(' > ');
  }

  function norm(s) {
    return (s || '').replace(/\s+/g, ' ').trim();
  }

  function lower(s) {
    return norm(s).toLowerCase();
  }

  function style(el, pseudo) {
    try { return getComputedStyle(el, pseudo || null); } catch (e) { return null; }
  }

  function rect(el) {
    var r = el.getBoundingClientRect();
    return { x: r.left, y: r.top, width: r.width, height: r.height };
  }

  function isVisible(el) {
    if (!el || el.nodeType !== 1) return false;
    if (el.getClientRects().length === 0) return false;
    var cs = style(el);
    if (!cs) return false;
    if (cs.visibility === 'hidden' || cs.visibility === 'collapse' || cs.display === 'none') return false;
    if (parseFloat(cs.opacity) === 0) return false;
    var r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) return false;
    return true;
  }

  function isHiddenFromAT(el) {
    var n = el;
    while (n && n.nodeType === 1) {
      if (n.getAttribute('aria-hidden') === 'true') return true;
      n = n.parentElement;
    }
    return false;
  }

  function snippet(el) {
    if (!el) return '';
    var html = el.outerHTML || '';
    return html.length > 300 ? html.slice(0, 300) + '…' : html;
  }

  function finding(outcome, el, message, data) {
    return {
      outcome: outcome,
      selector: el ? cssPath(el) : 'html',
      html: el ? snippet(el) : '',
      rect: el ? rect(el) : null,
      message: message,
      data: data || {}
    };
  }

  var INTERACTIVE_TAGS = { A: 1, BUTTON: 1, INPUT: 1, SELECT: 1, TEXTAREA: 1, SUMMARY: 1, DETAILS: 0, AREA: 1, IFRAME: 1, AUDIO: 1, VIDEO: 1, LABEL: 0 };
  var INTERACTIVE_ROLES = ['button', 'link', 'checkbox', 'radio', 'tab', 'menuitem', 'menuitemcheckbox', 'menuitemradio', 'switch', 'option', 'slider', 'spinbutton', 'textbox', 'combobox', 'searchbox', 'treeitem', 'gridcell', 'scrollbar'];

  function isNativelyInteractive(el) {
    var t = el.nodeName;
    if (t === 'A' || t === 'AREA') return el.hasAttribute('href');
    if (t === 'INPUT') return el.type !== 'hidden';
    if (t === 'AUDIO' || t === 'VIDEO') return el.hasAttribute('controls');
    return !!INTERACTIVE_TAGS[t];
  }

  function hasInteractiveRole(el) {
    var role = lower(el.getAttribute('role'));
    return role && INTERACTIVE_ROLES.indexOf(role.split(' ')[0]) >= 0;
  }

  function isInteractive(el) {
    return isNativelyInteractive(el) || hasInteractiveRole(el) || (el.hasAttribute('tabindex') && el.getAttribute('tabindex') !== '-1' && el.hasAttribute('onclick'));
  }

  function isFocusable(el) {
    if (!isVisible(el)) return false;
    if (el.disabled) return false;
    var ti = el.getAttribute('tabindex');
    if (ti !== null && !isNaN(parseInt(ti, 10))) return true;
    if (el.nodeName === 'A' || el.nodeName === 'AREA') return el.hasAttribute('href');
    if (el.nodeName === 'INPUT') return el.type !== 'hidden';
    if (el.nodeName === 'BUTTON' || el.nodeName === 'SELECT' || el.nodeName === 'TEXTAREA' || el.nodeName === 'SUMMARY' || el.nodeName === 'IFRAME') return true;
    if (el.isContentEditable) return true;
    if ((el.nodeName === 'AUDIO' || el.nodeName === 'VIDEO') && el.hasAttribute('controls')) return true;
    return false;
  }

  function isTabbable(el) {
    if (!isFocusable(el)) return false;
    var ti = el.getAttribute('tabindex');
    if (ti !== null && parseInt(ti, 10) < 0) return false;
    if (el.closest('[inert]')) return false;
    return true;
  }

  function tabbables() {
    var all = document.querySelectorAll('a[href],area[href],button,input,select,textarea,summary,iframe,audio[controls],video[controls],[tabindex],[contenteditable=""],[contenteditable="true"]');
    var list = Array.prototype.filter.call(all, isTabbable);
    var positive = list.filter(function (e) { return parseInt(e.getAttribute('tabindex') || '0', 10) > 0; });
    var zero = list.filter(function (e) { return parseInt(e.getAttribute('tabindex') || '0', 10) <= 0; });
    positive.sort(function (a, b) { return parseInt(a.getAttribute('tabindex'), 10) - parseInt(b.getAttribute('tabindex'), 10); });
    return positive.concat(zero);
  }

  function pseudoContent(el, which) {
    var cs = style(el, which);
    if (!cs) return '';
    var c = cs.content;
    if (!c || c === 'none' || c === 'normal') return '';
    var m = c.match(/^"(.*)"$|^'(.*)'$/);
    return m ? (m[1] || m[2] || '') : '';
  }

  function textFromSubtree(el, depth) {
    if (depth > 20) return '';
    var out = pseudoContent(el, '::before');
    el.childNodes.forEach(function (n) {
      if (n.nodeType === 3) out += n.nodeValue;
      else if (n.nodeType === 1) {
        if (n.getAttribute('aria-hidden') === 'true') return;
        var cs = style(n);
        if (cs && (cs.display === 'none' || cs.visibility === 'hidden')) return;
        var al = n.getAttribute('aria-label');
        if (al) { out += ' ' + al + ' '; return; }
        if (n.nodeName === 'IMG' || n.nodeName === 'AREA') { out += ' ' + (n.getAttribute('alt') || '') + ' '; return; }
        if (n.nodeName === 'SVG' || n.nodeName === 'svg') { var t = n.querySelector('title'); out += ' ' + (t ? t.textContent : '') + ' '; return; }
        if (n.nodeName === 'INPUT' && (n.type === 'button' || n.type === 'submit' || n.type === 'reset')) { out += ' ' + (n.value || '') + ' '; return; }
        out += ' ' + textFromSubtree(n, depth + 1) + ' ';
      }
    });
    out += pseudoContent(el, '::after');
    return out;
  }

  function labelText(el) {
    var texts = [];
    if (el.id) {
      document.querySelectorAll('label[for="' + esc(el.id) + '"]').forEach(function (l) { texts.push(textFromSubtree(l, 0)); });
    }
    var wrap = el.closest('label');
    if (wrap && texts.length === 0) texts.push(textFromSubtree(wrap, 0));
    return norm(texts.join(' '));
  }

  /* Simplified accessible name computation (accname 1.2 without the full recursion rules). */
  function accessibleName(el) {
    if (!el) return '';
    var lb = el.getAttribute('aria-labelledby');
    if (lb) {
      var parts = lb.split(/\s+/).map(function (id) {
        var ref = document.getElementById(id);
        return ref ? (ref.getAttribute('aria-label') || textFromSubtree(ref, 0)) : '';
      });
      var joined = norm(parts.join(' '));
      if (joined) return joined;
    }
    var al = el.getAttribute('aria-label');
    if (al && norm(al)) return norm(al);
    var tag = el.nodeName;
    if (tag === 'IMG' || tag === 'AREA') return norm(el.getAttribute('alt') || el.getAttribute('title') || '');
    if (tag === 'INPUT' && (el.type === 'button' || el.type === 'submit' || el.type === 'reset')) return norm(el.value || (el.type === 'submit' ? 'Submit' : el.type === 'reset' ? 'Reset' : ''));
    if (tag === 'INPUT' && el.type === 'image') return norm(el.getAttribute('alt') || el.value || '');
    if (tag === 'INPUT' || tag === 'SELECT' || tag === 'TEXTAREA') {
      var lt = labelText(el);
      if (lt) return lt;
      return norm(el.getAttribute('title') || el.getAttribute('placeholder') || '');
    }
    if (tag === 'svg' || tag === 'SVG') { var t = el.querySelector('title'); if (t) return norm(t.textContent); }
    if (tag === 'FIELDSET') { var lg = el.querySelector('legend'); if (lg) return norm(textFromSubtree(lg, 0)); }
    if (tag === 'TABLE') { var cap = el.querySelector('caption'); if (cap) return norm(textFromSubtree(cap, 0)); }
    var fromContent = norm(textFromSubtree(el, 0));
    if (fromContent) return fromContent;
    return norm(el.getAttribute('title') || '');
  }

  function visibleText(el) {
    try { return norm(el.innerText); } catch (e) { return norm(el.textContent); }
  }

  function contextText(el, max) {
    var block = el.closest('p, li, td, th, dd, dt, figcaption, blockquote, article, section, div, h1, h2, h3, h4, h5, h6');
    var t = block ? visibleText(block) : '';
    var db = el.getAttribute('aria-describedby');
    if (db) {
      db.split(/\s+/).forEach(function (id) { var r = document.getElementById(id); if (r) t += ' ' + visibleText(r); });
    }
    t = norm(t);
    return t.length > (max || 300) ? t.slice(0, max || 300) + '…' : t;
  }

  function parseColor(c) {
    var m = (c || '').match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    var p = m[1].split(',').map(function (v) { return parseFloat(v); });
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  }

  function sameColor(a, b) {
    var ca = parseColor(a), cb = parseColor(b);
    if (!ca || !cb) return a === b;
    return ca.r === cb.r && ca.g === cb.g && ca.b === cb.b && Math.abs(ca.a - cb.a) < 0.01;
  }

  function hasOwnText(el) {
    return Array.prototype.some.call(el.childNodes, function (n) { return n.nodeType === 3 && norm(n.nodeValue).length > 0; });
  }

  function ownTextLength(el) {
    var len = 0;
    el.childNodes.forEach(function (n) { if (n.nodeType === 3) len += norm(n.nodeValue).length; });
    return len;
  }

  function ownText(el) {
    var t = '';
    el.childNodes.forEach(function (n) { if (n.nodeType === 3) t += n.nodeValue; });
    return norm(t);
  }

  /* True when the element sits inside running text (a sentence), which is the "inline" exception of 2.5.8 and the text-block context of 1.4.1. */
  function inParagraphOfText(el) {
    var p = el.parentElement;
    var hops = 0;
    while (p && hops < 3) {
      var t = ownText(p);
      if (t.length >= 10 || (t.length >= 3 && /[.,;:!?]/.test(t))) return true;
      var cs = style(p);
      if (cs && cs.display !== 'inline') break;
      p = p.parentElement; hops++;
    }
    return false;
  }

  /* ------------------------------------------------------------------ rules */

  var rules = {};

  var GENERIC_ALT = /^(image|img|picture|photo|photograph|graphic|icon|logo|spacer|alt|banner|thumbnail|thumb|untitled|placeholder|blank|\d+)$/i;
  var FILENAME_ALT = /(\.(png|jpe?g|gif|svg|webp|avif|bmp|tiff?)$)|(^(img|image|dsc|dcim|screenshot|screen shot|photo|pic|untitled)[ _-]?\d+)|(^[a-z0-9_-]{6,}\.[a-z]{3,4}$)/i;
  var REDUNDANT_PREFIX = /^(image|picture|photo|photograph|graphic|icon|screenshot) (of|showing|depicting)\b/i;

  rules['alt-text-quality'] = function () {
    var out = [];
    var imgs = document.querySelectorAll('img, [role="img"]');
    imgs.forEach(function (img) {
      if (!isVisible(img) || isHiddenFromAT(img)) return;
      var role = lower(img.getAttribute('role'));
      if (role === 'presentation' || role === 'none') return;
      var alt = img.nodeName === 'IMG' ? img.getAttribute('alt') : img.getAttribute('aria-label');
      if (alt === null) return; // missing alt is covered by baseline tools; this rule judges quality
      var r = rect(img);
      var link = img.closest('a[href], button');
      var caption = img.closest('figure') ? (img.closest('figure').querySelector('figcaption') || {}).textContent : '';
      var data = {
        alt: alt,
        src: img.currentSrc || img.getAttribute('src') || '',
        width: Math.round(r.width),
        height: Math.round(r.height),
        inLink: !!link,
        linkText: link ? norm(textFromSubtree(link, 0)) : '',
        caption: norm(caption),
        context: contextText(img, 240)
      };
      var a = norm(alt);
      if (a === '') {
        if (link && !data.linkText) {
          out.push(finding('failed', img, 'Image is the only content of a link/button but has empty alt text, so the control has no name.', data));
        } else if (r.width >= 100 && r.height >= 60 && !link) {
          out.push(finding('needsReview', img, 'Large image (' + data.width + 'x' + data.height + ') marked decorative with alt="". Confirm it conveys no information.', data));
        }
        return;
      }
      if (FILENAME_ALT.test(a)) {
        out.push(finding('failed', img, 'Alt text looks like a file name: "' + a + '".', data));
        return;
      }
      if (GENERIC_ALT.test(a)) {
        out.push(finding('failed', img, 'Alt text is generic and does not describe the image: "' + a + '".', data));
        return;
      }
      if (REDUNDANT_PREFIX.test(a)) {
        out.push(finding('needsReview', img, 'Alt text starts with a redundant "image of"-style prefix: "' + a + '".', data));
        return;
      }
      if (a.length > 150) {
        out.push(finding('needsReview', img, 'Alt text is ' + a.length + ' characters. Consider a short alt plus a long description (figcaption / aria-describedby).', data));
        return;
      }
      if (link && lower(norm(link.textContent)) === lower(a)) {
        out.push(finding('needsReview', img, 'Alt text duplicates the adjacent link text; screen readers announce it twice.', data));
        return;
      }
      if (r.width >= 32 && r.height >= 32) {
        data.aiCandidate = true;
      }
      out.push(finding('passed', img, 'Alt text passes heuristics: "' + a + '".', data));
    });
    return out;
  };

  var GENERIC_LINK = /^(click here|here|read more|more|learn more|more info|more information|details|link|this|this link|this page|continue|go|view|see more|see all|info|download|open|start|next|previous|back|submit|website|page|article|→|>|»|\.\.\.|…)$/i;

  function linkTargets() {
    return Array.prototype.filter.call(document.querySelectorAll('a[href], [role="link"]'), function (a) {
      return isVisible(a) && !isHiddenFromAT(a);
    });
  }

  function normHref(a) {
    try { var u = new URL(a.getAttribute('href') || '', location.href); u.hash = ''; return u.href; } catch (e) { return a.getAttribute('href') || ''; }
  }

  rules['link-purpose-in-context'] = function () {
    var out = [];
    var links = linkTargets();
    var byName = {};
    links.forEach(function (a) {
      var name = lower(accessibleName(a));
      if (!name) return;
      (byName[name] = byName[name] || []).push(a);
    });
    links.forEach(function (a) {
      var name = accessibleName(a);
      if (!name) return;
      var data = { name: name, href: a.getAttribute('href'), context: contextText(a, 200) };
      var generic = GENERIC_LINK.test(norm(name));
      var contextual = data.context && lower(data.context) !== lower(name) && data.context.length > name.length + 10;
      var siblings = byName[lower(name)] || [];
      var distinctHrefs = {};
      siblings.forEach(function (s) { distinctHrefs[normHref(s)] = 1; });
      var nDistinct = Object.keys(distinctHrefs).length;
      if (generic && !contextual) {
        out.push(finding('failed', a, 'Link text "' + name + '" does not describe its purpose and no programmatically determinable context (sentence, list item, table cell, aria-describedby) was found.', data));
      } else if (generic) {
        out.push(finding('needsReview', a, 'Generic link text "' + name + '" relies on surrounding context. Verify the context makes the destination clear.', data));
      } else if (nDistinct > 1 && !contextual) {
        data.distinctDestinations = nDistinct;
        out.push(finding('needsReview', a, 'Link text "' + name + '" is used for ' + nDistinct + ' different destinations on this page without distinguishing context.', data));
      } else {
        out.push(finding('passed', a, 'Link purpose is determinable.', data));
      }
    });
    return out;
  };

  rules['link-purpose-link-only'] = function () {
    var out = [];
    linkTargets().forEach(function (a) {
      var name = accessibleName(a);
      if (!name) return;
      var data = { name: name, href: a.getAttribute('href') };
      if (GENERIC_LINK.test(norm(name))) {
        out.push(finding('failed', a, 'Link text "' + name + '" is not self-explanatory when read out of context (AAA).', data));
      } else {
        out.push(finding('passed', a, 'Link text is self-explanatory.', data));
      }
    });
    return out;
  };

  rules['use-of-color-links'] = function () {
    var out = [];
    linkTargets().forEach(function (a) {
      if (!inParagraphOfText(a)) return;
      var parent = a.parentElement;
      var cs = style(a), ps = style(parent);
      if (!cs || !ps) return;
      var data = { color: cs.color, parentColor: ps.color, textDecoration: cs.textDecorationLine, fontWeight: cs.fontWeight, parentFontWeight: ps.fontWeight };
      if (sameColor(cs.color, ps.color)) return; // not distinguished by color at all; other rules cover
      var decorated = cs.textDecorationLine && cs.textDecorationLine !== 'none';
      var border = parseFloat(cs.borderBottomWidth) > 0 && cs.borderBottomStyle !== 'none';
      var bg = !sameColor(cs.backgroundColor, ps.backgroundColor) && parseColor(cs.backgroundColor) && parseColor(cs.backgroundColor).a > 0;
      var weight = cs.fontWeight !== ps.fontWeight;
      var italic = cs.fontStyle !== ps.fontStyle;
      var outline = cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) > 0;
      var before = pseudoContent(a, '::before') || pseudoContent(a, '::after');
      if (decorated || border || bg || weight || italic || outline || before) {
        out.push(finding('passed', a, 'Inline link is distinguished by more than colour.', data));
      } else {
        out.push(finding('failed', a, 'Inline link in a block of text is distinguished from surrounding text by colour alone (no underline, border, weight or other cue). WCAG requires a 3:1 contrast difference plus a non-colour cue on hover/focus, or a non-colour cue at rest.', data));
      }
    });
    return out;
  };

  var SENSORY = [
    /\b(click|press|select|tap|use|choose|hit)\b[^.]{0,40}\b(red|green|blue|yellow|orange|purple|pink|gr[ae]y|black|white|round|square|circular|big|large|small|little)\b[^.]{0,20}\b(button|icon|link|arrow|box|tab|menu|control)/i,
    /\b(button|link|icon|menu|image|box|option|field|form|section|panel|column|list)\b[^.]{0,20}\b(below|above|to the right|to the left|on the right|on the left|at the top|at the bottom)\b/i,
    /\b(right|left)[- ]hand (side|column|menu|corner|panel|navigation|nav)\b/i,
    /\bthe (red|green|blue|yellow|orange|purple|pink|gr[ae]y) (button|link|icon|text|box|area|arrow)\b/i,
    /\b(see|shown|located|find|found|listed)\b[^.]{0,20}\b(below|above|on the right|on the left|to the right|to the left)\b/i,
    /\bwhen you hear (the|a) (beep|sound|tone|chime)\b/i,
    /\b(round|square|triangular|circular) (button|icon)\b/i
  ];

  rules['sensory-characteristics'] = function () {
    var out = [];
    var seen = {};
    var blocks = document.querySelectorAll('p, li, td, th, dd, dt, label, legend, figcaption, caption, summary, h1, h2, h3, h4, h5, h6, span, div, a, button');
    blocks.forEach(function (el) {
      if (!isVisible(el) || !hasOwnText(el)) return;
      var t = visibleText(el);
      if (!t || t.length > 600) return;
      for (var i = 0; i < SENSORY.length; i++) {
        var m = t.match(SENSORY[i]);
        if (m) {
          var key = lower(m[0]);
          if (seen[key]) return;
          seen[key] = 1;
          out.push(finding('needsReview', el, 'Instruction may rely on sensory characteristics (shape, colour, size, location or sound): "' + m[0] + '". Confirm the same information is also conveyed by text or structure.', { match: m[0], text: t.slice(0, 200) }));
          return;
        }
      }
    });
    return out;
  };

  function pointerTargets() {
    var sel = 'a[href], area[href], button, input:not([type="hidden"]), select, textarea, summary, [role="button"], [role="link"], [role="checkbox"], [role="radio"], [role="tab"], [role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"], [role="switch"], [role="option"], [role="slider"], [role="spinbutton"], [role="combobox"], [onclick], [tabindex]:not([tabindex="-1"])';
    var list = Array.prototype.filter.call(document.querySelectorAll(sel), function (el) {
      if (!isVisible(el) || isHiddenFromAT(el)) return false;
      if (el.nodeName === 'INPUT' && (el.type === 'checkbox' || el.type === 'radio')) {
        // a wrapping/associated label enlarges the target
        return true;
      }
      // ignore containers whose only purpose is to wrap a real control
      if (!isNativelyInteractive(el) && !hasInteractiveRole(el) && el.querySelector('a[href],button,input,select,textarea')) return false;
      return true;
    });
    return list;
  }

  function effectiveTargetRect(el) {
    var r = el.getBoundingClientRect();
    var box = { left: r.left, top: r.top, right: r.right, bottom: r.bottom };
    if (el.nodeName === 'INPUT' && (el.type === 'checkbox' || el.type === 'radio')) {
      var label = el.closest('label') || (el.id ? document.querySelector('label[for="' + esc(el.id) + '"]') : null);
      if (label && isVisible(label)) {
        var lr = label.getBoundingClientRect();
        box = { left: Math.min(box.left, lr.left), top: Math.min(box.top, lr.top), right: Math.max(box.right, lr.right), bottom: Math.max(box.bottom, lr.bottom) };
      }
    }
    // If the element paints nothing but a child does (e.g. <a><img>), use the union of child rects.
    if (r.width === 0 || r.height === 0) {
      Array.prototype.forEach.call(el.children, function (c) {
        var cr = c.getBoundingClientRect();
        if (cr.width && cr.height) box = { left: Math.min(box.left, cr.left), top: Math.min(box.top, cr.top), right: Math.max(box.right, cr.right), bottom: Math.max(box.bottom, cr.bottom) };
      });
    }
    return { x: box.left, y: box.top, width: box.right - box.left, height: box.bottom - box.top };
  }

  function isInlineTarget(el) {
    var cs = style(el);
    if (!cs || cs.display !== 'inline') return false;
    return inParagraphOfText(el);
  }

  function circleIntersectsRect(cx, cy, radius, r) {
    var nx = Math.max(r.x, Math.min(cx, r.x + r.width));
    var ny = Math.max(r.y, Math.min(cy, r.y + r.height));
    var dx = cx - nx, dy = cy - ny;
    return (dx * dx + dy * dy) < radius * radius;
  }

  function targetSize(minimum, level) {
    var out = [];
    var targets = pointerTargets();
    var rects = targets.map(effectiveTargetRect);
    targets.forEach(function (el, i) {
      var r = rects[i];
      if (r.width <= 0 || r.height <= 0) return;
      var data = { width: Math.round(r.width), height: Math.round(r.height), minimum: minimum };
      if (r.width >= minimum && r.height >= minimum) {
        out.push(finding('passed', el, 'Target is ' + data.width + 'x' + data.height + ' CSS px.', data));
        return;
      }
      if (isInlineTarget(el)) {
        data.exception = 'inline';
        out.push(finding('passed', el, 'Undersized target (' + data.width + 'x' + data.height + ') is inline within a sentence or block of text (exception).', data));
        return;
      }
      if (minimum === 24) {
        // Spacing exception: a 24px circle centred on the target must not intersect other targets or their circles.
        var cx = r.x + r.width / 2, cy = r.y + r.height / 2;
        var clash = null;
        for (var j = 0; j < targets.length && !clash; j++) {
          if (j === i) continue;
          var o = rects[j];
          if (o.width <= 0 || o.height <= 0) continue;
          if (circleIntersectsRect(cx, cy, 12, o)) { clash = targets[j]; break; }
          if (o.width < 24 || o.height < 24) {
            var ox = o.x + o.width / 2, oy = o.y + o.height / 2;
            if (Math.hypot(cx - ox, cy - oy) < 24) { clash = targets[j]; break; }
          }
        }
        if (!clash) {
          data.exception = 'spacing';
          out.push(finding('passed', el, 'Undersized target (' + data.width + 'x' + data.height + ') has sufficient spacing from other targets (exception).', data));
          return;
        }
        data.clashesWith = cssPath(clash);
      }
      out.push(finding('failed', el, 'Target is ' + data.width + 'x' + data.height + ' CSS px, below the ' + minimum + 'x' + minimum + ' ' + level + ' minimum' + (data.clashesWith ? ', and too close to ' + data.clashesWith : '') + '.', data));
    });
    return out;
  }

  rules['target-size-minimum'] = function () { return targetSize(24, 'AA'); };
  rules['target-size-enhanced'] = function () { return targetSize(44, 'AAA'); };

  /* Widget roles that take their name from content, so a visible label can conflict with an author name. */
  var NAME_FROM_CONTENT_WIDGETS = ['button', 'checkbox', 'gridcell', 'link', 'menuitem', 'menuitemcheckbox', 'menuitemradio', 'option', 'radio', 'searchbox', 'switch', 'tab', 'treeitem'];
  var ICON_FONT = /material icons|material symbols|font ?awesome|glyphicons|icomoon|dashicons|ionicons|feather|octicons/i;

  /*
   * The label-in-name comparison: lower case, drop parenthesised content (per the ACT algorithm),
   * drop punctuation and symbols, and collapse whitespace to a word list.
   */
  function labelWords(s) {
    return norm(String(s || '').toLowerCase().replace(/\([^)]*\)/g, ' ').replace(/[^\p{L}\p{N}\s]/gu, ' ')).split(/\s+/).filter(Boolean);
  }

  function containsWordSequence(haystack, needle) {
    if (!needle.length) return true;
    for (var i = 0; i + needle.length <= haystack.length; i++) {
      var ok = true;
      for (var j = 0; j < needle.length; j++) {
        if (haystack[i + j] !== needle[j]) { ok = false; break; }
      }
      if (ok) return true;
    }
    return false;
  }

  /* Inner text as a sighted user reads it: visually hidden helper text is excluded. */
  function visibleInnerText(el, depth) {
    var out = '';
    for (var i = 0; i < el.childNodes.length; i++) {
      var n = el.childNodes[i];
      if (n.nodeType === 3) { out += n.nodeValue; continue; }
      if (n.nodeType !== 1) continue;
      var cs = style(n);
      if (!cs || cs.display === 'none' || cs.visibility === 'hidden') continue;
      var r = n.getBoundingClientRect();
      var clipped = (cs.clipPath && cs.clipPath !== 'none' && /inset\(\s*50%/.test(cs.clipPath)) || (r.width <= 1 && r.height <= 1);
      if (clipped) continue;
      out += ' ' + ((depth || 0) < 6 ? visibleInnerText(n, (depth || 0) + 1) : norm(n.textContent)) + ' ';
    }
    return out;
  }

  rules['label-in-name'] = function () {
    var out = [];
    var sel = 'a[href], button, summary, input, select, textarea, [role]';
    document.querySelectorAll(sel).forEach(function (el) {
      if (!isVisible(el) || !inAccessibilityTree(el)) return;
      if (!el.hasAttribute('aria-label') && !el.hasAttribute('aria-labelledby')) return;
      var role = computedRole(el);
      if (NAME_FROM_CONTENT_WIDGETS.indexOf(role) < 0) return;
      var name = norm(accessibleName(el));
      var visible = norm(visibleInnerText(el, 0));
      if (!visible || !name) return;
      var data = { accessibleName: name, visibleLabel: visible, role: role };
      var cs = style(el);
      if (cs && ICON_FONT.test(cs.fontFamily)) {
        out.push(fc('cantTell', el, 'label-in-name', 'The visible text "' + visible + '" is rendered with an icon font, so it is a symbol rather than a readable label; confirm by eye whether the accessible name "' + name + '" matches what is shown.', data));
        return;
      }
      var visibleWords = labelWords(visible);
      var nameWords = labelWords(name);
      if (!visibleWords.length) return;
      // a lone capital letter is usually an abbreviation or an icon stand-in, which ACT excludes
      var singleCapital = norm(visible.replace(/\([^)]*\)/g, ' ')).split(/\s+/).some(function (w) { return /^\p{Lu}$/u.test(w); });
      if (singleCapital) {
        out.push(fc('cantTell', el, 'label-in-name', 'The visible text "' + visible + '" contains a single-letter token, which may be an abbreviation or icon; ACT excludes abbreviations from this rule, so confirm manually against the accessible name "' + name + '".', data));
        return;
      }
      if (containsWordSequence(nameWords, visibleWords)) {
        out.push(fc('passed', el, 'label-in-name', 'Accessible name contains the visible label.', data));
      } else {
        out.push(fc('failed', el, 'label-in-name', 'Accessible name "' + name + '" does not contain the visible label "' + visible + '". Speech-input users cannot activate it by saying what they see.', data));
      }
    });
    return out;
  };

  rules['pause-stop-hide'] = function () {
    var out = [];
    document.querySelectorAll('marquee, blink').forEach(function (el) {
      if (isVisible(el)) out.push(finding('failed', el, '<' + el.nodeName.toLowerCase() + '> content moves/blinks and cannot be paused, stopped or hidden.', {}));
    });
    document.querySelectorAll('video, audio').forEach(function (m) {
      if (!m.autoplay && !(m.hasAttribute('autoplay'))) return;
      var data = { controls: m.hasAttribute('controls'), muted: m.muted, loop: m.loop, duration: isFinite(m.duration) ? m.duration : null };
      if (m.nodeName === 'VIDEO' && !isVisible(m)) return;
      if (!data.controls) {
        out.push(finding('failed', m, 'Auto-playing <' + m.nodeName.toLowerCase() + '> has no controls, so it cannot be paused or stopped.', data));
      } else if (m.loop || data.duration === null || data.duration > 5) {
        out.push(finding('needsReview', m, 'Auto-playing media longer than 5 seconds. Confirm the pause/stop control is keyboard accessible and discoverable.', data));
      }
    });
    if (document.getAnimations) {
      var seen = {};
      document.getAnimations().forEach(function (anim) {
        var effect = anim.effect;
        if (!effect || !effect.target || !effect.getTiming) return;
        var t = effect.getTiming();
        var total = t.iterations === Infinity ? Infinity : (t.duration || 0) * (t.iterations || 1);
        if (!(total === Infinity || total > 5000)) return;
        var el = effect.target;
        if (!isVisible(el)) return;
        var key = cssPath(el);
        if (seen[key]) return;
        seen[key] = 1;
        var data = { iterations: t.iterations === Infinity ? 'infinite' : t.iterations, durationMs: t.duration, reducedMotion: matchMedia('(prefers-reduced-motion: reduce)').matches };
        out.push(finding('needsReview', el, 'Element animates for more than 5 seconds (' + data.iterations + ' iterations). A mechanism to pause, stop or hide it is required unless the animation is essential.', data));
      });
    }
    return out;
  };

  rules['timing-adjustable'] = function () {
    var out = [];
    var first = true;
    document.querySelectorAll('meta[http-equiv]').forEach(function (m) {
      if (lower(m.getAttribute('http-equiv')) !== 'refresh') return;
      var content = norm(m.getAttribute('content') || '');
      // only the first syntactically valid refresh directive in the document has any effect
      if (!/^\d+\s*(;.*)?$/.test(content)) return;
      if (!first) return;
      first = false;
      var secs = parseInt(content, 10);
      var data = { content: content, seconds: secs };
      if (secs === 0) {
        out.push(fc('passed', m, 'meta-refresh', 'Immediate client-side redirect (delay 0) is exempt from the time-limit requirement.', data));
        out.push(fc('failed', m, 'meta-refresh-strict', 'Page redirects automatically via meta refresh; at AAA (2.2.3 No Timing) no timing is allowed, including a zero delay.', data));
      } else if (secs > 72000) {
        out.push(fc('passed', m, 'meta-refresh', 'Refresh delay of ' + secs + 's exceeds 20 hours and is exempt.', data));
        out.push(fc('failed', m, 'meta-refresh-strict', 'Page refreshes automatically after ' + secs + 's; at AAA no timing is allowed.', data));
      } else {
        out.push(fc('failed', m, 'meta-refresh', 'Page refreshes or redirects automatically after ' + secs + 's with no way to turn off, adjust or extend the time limit (F40/F41).', data));
        out.push(fc('failed', m, 'meta-refresh-strict', 'Page refreshes automatically after ' + secs + 's; at AAA no timing is allowed.', data));
      }
    });
    return out;
  };

  function captchaElements() {
    var list = [];
    document.querySelectorAll('iframe[src], script[src], div[class], img[alt], img[src], input[name], [id]').forEach(function (el) {
      var hay = lower((el.getAttribute('src') || '') + ' ' + (el.getAttribute('class') || '') + ' ' + (el.getAttribute('alt') || '') + ' ' + (el.getAttribute('name') || '') + ' ' + (el.id || ''));
      if (/recaptcha|hcaptcha|turnstile|captcha|funcaptcha|arkose/.test(hay)) list.push(el);
    });
    return list;
  }

  rules['accessible-authentication-minimum'] = function () {
    var out = [];
    var pw = document.querySelectorAll('input[type="password"]');
    if (pw.length === 0) return out;
    pw.forEach(function (p) {
      var form = p.form || p.closest('form') || document.body;
      var data = { autocomplete: p.getAttribute('autocomplete'), onpaste: p.getAttribute('onpaste') };
      var problems = [];
      if (p.getAttribute('onpaste') && /return\s+false|preventDefault/i.test(p.getAttribute('onpaste'))) problems.push('paste is blocked');
      if (lower(p.getAttribute('autocomplete')) === 'off') problems.push('autocomplete="off" hinders password managers');
      var user = form.querySelector('input[type="email"], input[type="text"], input[autocomplete*="username"]');
      if (user) {
        data.usernameAutocomplete = user.getAttribute('autocomplete');
        if (user.getAttribute('onpaste') && /return\s+false|preventDefault/i.test(user.getAttribute('onpaste'))) problems.push('paste is blocked on the username field');
        if (lower(user.getAttribute('autocomplete')) === 'off') problems.push('username field has autocomplete="off"');
      }
      if (problems.length) {
        out.push(finding('failed', p, 'Login form relies on the user transcribing or memorising credentials: ' + problems.join('; ') + '. Allow paste and password managers, or provide another non-cognitive alternative.', data));
      } else {
        out.push(finding('passed', p, 'Password field permits paste and password manager use.', data));
      }
    });
    captchaElements().forEach(function (el) {
      out.push(finding('needsReview', el, 'CAPTCHA detected on an authentication page. A cognitive function test is only allowed with an alternative that does not require one (e.g. email magic link, WebAuthn) or if it is object recognition/personal content.', { snippet: snippet(el) }));
    });
    document.querySelectorAll('input[inputmode="numeric"], input[name*="otp" i], input[name*="code" i], input[autocomplete="one-time-code"]').forEach(function (el) {
      if (!isVisible(el)) return;
      if (el.getAttribute('autocomplete') === 'one-time-code') return;
      out.push(finding('needsReview', el, 'Likely one-time-code field without autocomplete="one-time-code". Transcribing a code is a cognitive function test unless it can be pasted or auto-filled.', { name: el.getAttribute('name') }));
    });
    return out;
  };

  rules['accessible-authentication-enhanced'] = function () {
    var out = [];
    if (document.querySelectorAll('input[type="password"]').length === 0) return out;
    captchaElements().forEach(function (el) {
      out.push(finding('failed', el, 'CAPTCHA on an authentication step. At AAA, object recognition and personal-content exceptions do not apply; an alternative without a cognitive function test is required.', {}));
    });
    return out;
  };

  rules['dragging-movements'] = function () {
    var out = [];
    var sel = '[draggable="true"], [ondragstart], .ui-draggable, .ui-sortable, .react-draggable, [data-rbd-draggable-id], [data-rfd-draggable-id], .sortable-item, .draggable, .dnd-item, .dragula-container > *, [class*="drag-handle"], [class*="dragHandle"]';
    var seen = {};
    document.querySelectorAll(sel).forEach(function (el) {
      if (!isVisible(el)) return;
      var key = cssPath(el);
      if (seen[key]) return;
      seen[key] = 1;
      out.push(finding('needsReview', el, 'Element appears to support dragging. Ensure the same outcome is achievable with a single pointer without dragging (e.g. move up/down buttons) unless dragging is essential.', { hint: el.getAttribute('draggable') ? 'draggable attribute' : 'library class/attribute' }));
    });
    return out;
  };

  var PURPOSE = [
    { re: /\b(e-?mail)\b/i, token: 'email' },
    { re: /\b(given|first) ?name\b|\bforename\b/i, token: 'given-name' },
    { re: /\b(family|last|sur) ?name\b/i, token: 'family-name' },
    { re: /\b(full ?name|your name|name)\b/i, token: 'name' },
    { re: /\b(tel(ephone)?|phone|mobile|cell)\b/i, token: 'tel' },
    { re: /\b(street|address ?line ?1|address1|address)\b/i, token: 'street-address' },
    { re: /\b(postal ?code|post ?code|zip( ?code)?)\b/i, token: 'postal-code' },
    { re: /\b(city|town|locality)\b/i, token: 'address-level2' },
    { re: /\b(state|province|region)\b/i, token: 'address-level1' },
    { re: /\bcountry\b/i, token: 'country-name' },
    { re: /\b(card ?number|credit ?card|cc-?number)\b/i, token: 'cc-number' },
    { re: /\b(birth ?date|date of birth|dob|birthday)\b/i, token: 'bday' },
    { re: /\b(organi[sz]ation|company|employer)\b/i, token: 'organization' },
    { re: /\b(user ?name|login|user id)\b/i, token: 'username' },
    { re: /\b(new password|confirm password)\b/i, token: 'new-password' },
    { re: /\bpassword\b/i, token: 'current-password' }
  ];

  rules['identify-input-purpose'] = function () {
    var out = [];
    var inputs = document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="reset"]):not([type="checkbox"]):not([type="radio"]):not([type="file"]):not([type="search"]), select, textarea');
    inputs.forEach(function (el) {
      if (!isVisible(el)) return;
      var hay = [labelText(el), el.getAttribute('aria-label'), el.getAttribute('placeholder'), el.getAttribute('name'), el.id, el.getAttribute('title')].map(lower).join(' | ');
      var expected = null;
      for (var i = 0; i < PURPOSE.length; i++) { if (PURPOSE[i].re.test(hay)) { expected = PURPOSE[i].token; break; } }
      if (!expected) return;
      var ac = lower(el.getAttribute('autocomplete'));
      var data = { expectedToken: expected, autocomplete: ac || null, hints: hay };
      if (!ac || ac === 'off' || ac === 'on') {
        out.push(finding('failed', el, 'Field collects "' + expected + '" but has no autocomplete token (' + (ac ? 'autocomplete="' + ac + '"' : 'none') + '). Add autocomplete="' + expected + '" so purpose is programmatically determinable.', data));
      } else {
        out.push(finding('passed', el, 'Input purpose identified via autocomplete="' + ac + '".', data));
      }
    });
    return out;
  };

  var GENERIC_HEADING = /^(heading|title|untitled|header|section|subtitle|h[1-6]|lorem ipsum.*|placeholder|todo|tbd|new heading|text|label|field|input)$/i;

  rules['headings-and-labels-descriptive'] = function () {
    var out = [];
    var prev = null;
    document.querySelectorAll('h1, h2, h3, h4, h5, h6, [role="heading"]').forEach(function (h) {
      if (!isVisible(h) || isHiddenFromAT(h)) return;
      var t = norm(accessibleName(h));
      if (!t) return; // empty headings are covered by baseline tools
      var lvl = h.getAttribute('aria-level') || h.nodeName.replace('H', '');
      var data = { text: t, level: lvl };
      if (GENERIC_HEADING.test(t)) {
        out.push(finding('failed', h, 'Heading text "' + t + '" is a placeholder or generic and does not describe the section.', data));
      } else if (prev && prev.text === lower(t) && prev.level === lvl) {
        out.push(finding('needsReview', h, 'Consecutive headings at the same level have identical text "' + t + '". Users cannot distinguish the sections from the heading list.', data));
      } else if (t.length > 160) {
        out.push(finding('needsReview', h, 'Heading is ' + t.length + ' characters long; long headings are hard to scan in heading lists.', data));
      } else {
        out.push(finding('passed', h, 'Heading is descriptive: "' + t + '".', data));
      }
      prev = { text: lower(t), level: lvl };
    });
    document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="reset"]), select, textarea').forEach(function (el) {
      if (!isVisible(el)) return;
      var name = norm(accessibleName(el));
      if (!name) return;
      if (GENERIC_HEADING.test(name) || /^(enter|type|select|choose)( here| value| text)?$/i.test(name)) {
        out.push(finding('failed', el, 'Form control label "' + name + '" does not describe the required input.', { label: name }));
      }
    });
    return out;
  };

  /* A rotation that is a whole number of turns leaves the content the right way up. */
  function rotatesVisibly(transform) {
    var m = /rotate(?:[XYZ]|3d\([^)]*\))?\(\s*(-?[\d.]+)(deg|grad|rad|turn)\s*\)/i.exec(transform || '');
    if (!m) return false;
    var v = parseFloat(m[1]);
    var deg = m[2] === 'turn' ? v * 360 : m[2] === 'rad' ? v * 180 / Math.PI : m[2] === 'grad' ? v * 0.9 : v;
    return Math.abs(deg % 360) > 0.5;
  }

  rules['orientation'] = function () {
    var out = [];
    var sheets = document.styleSheets;
    var found = false;
    for (var i = 0; i < sheets.length; i++) {
      var rulesList;
      try { rulesList = sheets[i].cssRules; } catch (e) { continue; }
      if (!rulesList) continue;
      for (var j = 0; j < rulesList.length; j++) {
        var r = rulesList[j];
        if (!(r.media && /orientation/i.test(r.media.mediaText))) continue;
        found = true;
        var hides = false, rotates = false;
        for (var k = 0; k < r.cssRules.length; k++) {
          var inner = r.cssRules[k];
          if (!inner.style) continue;
          var sel = lower(inner.selectorText);
          var isRoot = /^(html|body|#root|#app|main|\.app|\.page|\*)(\s*,|$)/.test(sel) || /\bbody\b|\bhtml\b/.test(sel);
          if (isRoot && (inner.style.display === 'none' || inner.style.visibility === 'hidden')) hides = true;
          if (rotatesVisibly(inner.style.transform || '')) rotates = true;
        }
        var data = { media: r.media.mediaText, sheet: sheets[i].href || 'inline' };
        if (hides) out.push(finding('failed', null, 'Stylesheet hides the page in one orientation (' + r.media.mediaText + '). Content must not be restricted to a single display orientation unless essential.', data));
        else if (rotates) out.push(finding('failed', null, 'Stylesheet rotates the page in one orientation (' + r.media.mediaText + '), forcing users to turn their device.', data));
        else out.push(finding('needsReview', null, 'Orientation-specific styles found (' + r.media.mediaText + '). Confirm all content and functionality remain available in both orientations.', data));
      }
    }
    if (!found && screen.orientation) {
      out.push(finding('passed', null, 'No orientation-specific CSS media rules detected.', {}));
    }
    return out;
  };

  rules['keyboard-operable-controls'] = function () {
    var out = [];
    var all = document.querySelectorAll('body *');
    all.forEach(function (el) {
      if (!isVisible(el) || isHiddenFromAT(el)) return;
      if (isNativelyInteractive(el)) return;
      var cs = style(el);
      var hasOnClick = el.hasAttribute('onclick');
      var role = hasInteractiveRole(el);
      var pointer = cs && cs.cursor === 'pointer';
      if (!hasOnClick && !role && !pointer) return;
      // inside a real control or a custom control: the ancestor is the target, not this node
      if (el.closest('a[href], button, input, select, textarea, summary, label')) return;
      var parent = el.parentElement;
      if (parent && parent.closest('[role="button"], [role="link"], [role="tab"], [role="menuitem"], [role="checkbox"], [role="radio"], [role="option"], [role="switch"], [tabindex]')) return;
      if (el.querySelector('a[href], button, input, select, textarea, summary, [tabindex]')) return; // wrapper around a real control
      var focusable = isFocusable(el);
      var data = { role: el.getAttribute('role'), onclick: hasOnClick, cursorPointer: pointer, tabindex: el.getAttribute('tabindex') };
      if ((hasOnClick || role) && !focusable) {
        out.push(finding('failed', el, '<' + el.nodeName.toLowerCase() + '> acts as a control (' + (role ? 'role=' + el.getAttribute('role') : 'onclick handler') + ') but is not keyboard focusable. Add tabindex="0" and key handling, or use a native <button>/<a>.', data));
      } else if ((hasOnClick || role) && focusable) {
        out.push(finding('needsReview', el, 'Custom control is focusable; verify Enter/Space (and arrow keys where the pattern requires) activate it.', data));
      } else if (pointer && !focusable && !role && !hasOnClick && el.nodeName !== 'LABEL') {
        out.push(finding('needsReview', el, 'Element has cursor:pointer but is neither natively interactive nor focusable. If it responds to clicks it is not keyboard operable.', data));
      }
    });
    return out;
  };

  rules['content-on-hover-title'] = function () {
    var out = [];
    document.querySelectorAll('[title]').forEach(function (el) {
      if (!isVisible(el)) return;
      var title = norm(el.getAttribute('title'));
      if (!title) return;
      if (el.nodeName === 'IFRAME' || el.nodeName === 'ABBR' || el.nodeName === 'SVG' || el.nodeName === 'svg') return;
      var vt = lower(visibleText(el));
      var name = lower(accessibleName(el));
      if (vt === lower(title) || name === lower(title)) return; // purely redundant title
      out.push(finding('needsReview', el, 'Additional content is exposed only via a native title tooltip ("' + title + '"). Native tooltips are not dismissible, hoverable or keyboard-triggerable, and are unavailable to touch users.', { title: title }));
    });
    return out;
  };

  /* ------------------------------------------------ DOM analysis (baseline) */

  var VALID_ROLES = ['alert', 'alertdialog', 'application', 'article', 'banner', 'blockquote', 'button', 'caption', 'cell', 'checkbox', 'code', 'columnheader', 'combobox', 'comment', 'complementary', 'contentinfo', 'definition', 'deletion', 'dialog', 'directory', 'document', 'emphasis', 'feed', 'figure', 'form', 'generic', 'grid', 'gridcell', 'group', 'heading', 'img', 'image', 'insertion', 'link', 'list', 'listbox', 'listitem', 'log', 'main', 'mark', 'marquee', 'math', 'menu', 'menubar', 'menuitem', 'menuitemcheckbox', 'menuitemradio', 'meter', 'navigation', 'none', 'note', 'option', 'paragraph', 'presentation', 'progressbar', 'radio', 'radiogroup', 'region', 'row', 'rowgroup', 'rowheader', 'scrollbar', 'search', 'searchbox', 'separator', 'slider', 'spinbutton', 'status', 'strong', 'subscript', 'suggestion', 'superscript', 'switch', 'tab', 'table', 'tablist', 'tabpanel', 'term', 'textbox', 'time', 'timer', 'toolbar', 'tooltip', 'tree', 'treegrid', 'treeitem'];
  var VALID_ARIA = ['aria-activedescendant', 'aria-atomic', 'aria-autocomplete', 'aria-braillelabel', 'aria-brailleroledescription', 'aria-busy', 'aria-checked', 'aria-colcount', 'aria-colindex', 'aria-colindextext', 'aria-colspan', 'aria-controls', 'aria-current', 'aria-describedby', 'aria-description', 'aria-details', 'aria-disabled', 'aria-dropeffect', 'aria-errormessage', 'aria-expanded', 'aria-flowto', 'aria-grabbed', 'aria-haspopup', 'aria-hidden', 'aria-invalid', 'aria-keyshortcuts', 'aria-label', 'aria-labelledby', 'aria-level', 'aria-live', 'aria-modal', 'aria-multiline', 'aria-multiselectable', 'aria-orientation', 'aria-owns', 'aria-placeholder', 'aria-posinset', 'aria-pressed', 'aria-readonly', 'aria-relevant', 'aria-required', 'aria-roledescription', 'aria-rowcount', 'aria-rowindex', 'aria-rowindextext', 'aria-rowspan', 'aria-selected', 'aria-setsize', 'aria-sort', 'aria-valuemax', 'aria-valuemin', 'aria-valuenow', 'aria-valuetext'];
  var NAME_PROHIBITED_ROLES = ['caption', 'code', 'deletion', 'emphasis', 'generic', 'insertion', 'mark', 'none', 'paragraph', 'presentation', 'strong', 'subscript', 'superscript', 'suggestion', 'term', 'time'];
  var GENERIC_TAGS = ['DIV', 'SPAN', 'P', 'CODE', 'EM', 'STRONG', 'SUB', 'SUP', 'TIME', 'DEL', 'INS', 'B', 'I', 'U', 'SMALL', 'S', 'MARK', 'BDI', 'BDO', 'Q', 'PRE', 'BLOCKQUOTE', 'HR', 'BR', 'WBR'];
  var IDREF_ATTRS = ['aria-labelledby', 'aria-describedby', 'aria-controls', 'aria-owns', 'aria-flowto', 'aria-details', 'aria-errormessage', 'aria-activedescendant'];
  var ID_ATTRS_STRICT = { 'aria-labelledby': 1, 'aria-describedby': 1, 'aria-activedescendant': 1, 'aria-errormessage': 1, 'aria-details': 1, 'aria-flowto': 1 };
  var ID_ATTRS_SINGLE = { 'aria-activedescendant': 1, 'aria-errormessage': 1, 'aria-details': 1 };
  var GLOBAL_ARIA = ['aria-atomic', 'aria-braillelabel', 'aria-brailleroledescription', 'aria-busy', 'aria-controls', 'aria-current', 'aria-describedby', 'aria-description', 'aria-details', 'aria-dropeffect', 'aria-flowto', 'aria-grabbed', 'aria-hidden', 'aria-keyshortcuts', 'aria-label', 'aria-labelledby', 'aria-live', 'aria-owns', 'aria-relevant', 'aria-roledescription'];
  var REQUIRED_ATTRS = { checkbox: ['aria-checked'], menuitemcheckbox: ['aria-checked'], switch: ['aria-checked'], radio: ['aria-checked'], menuitemradio: ['aria-checked'], combobox: ['aria-expanded'], heading: ['aria-level'], meter: ['aria-valuenow'], scrollbar: ['aria-controls', 'aria-valuenow'], slider: ['aria-valuenow'], separator: ['aria-valuenow'], option: ['aria-selected'] };
  var REQUIRED_CONTEXT = { listitem: ['list', 'directory'], option: ['listbox', 'group'], tab: ['tablist'], menuitem: ['menu', 'menubar', 'group'], menuitemcheckbox: ['menu', 'menubar', 'group'], menuitemradio: ['menu', 'menubar', 'group'], row: ['table', 'grid', 'treegrid', 'rowgroup'], rowgroup: ['table', 'grid', 'treegrid'], cell: ['row'], gridcell: ['row'], columnheader: ['row'], rowheader: ['row'], treeitem: ['tree', 'group'] };
  var IMPLICIT_ROLES = { UL: 'list', OL: 'list', MENU: 'list', LI: 'listitem', TABLE: 'table', TBODY: 'rowgroup', THEAD: 'rowgroup', TFOOT: 'rowgroup', TR: 'row', TD: 'cell', TH: 'columnheader', SELECT: 'listbox', DATALIST: 'listbox', OPTION: 'option', NAV: 'navigation', MAIN: 'main', HEADER: 'banner', FOOTER: 'contentinfo', ASIDE: 'complementary', FORM: 'form', BUTTON: 'button', A: 'link', IMG: 'img', H1: 'heading', H2: 'heading', H3: 'heading', H4: 'heading', H5: 'heading', H6: 'heading', DIALOG: 'dialog', ARTICLE: 'article', SECTION: 'region', TEXTAREA: 'textbox', PROGRESS: 'progressbar', METER: 'meter', HR: 'separator', AUDIO: 'audio', VIDEO: 'video', SUMMARY: 'button', DETAILS: 'group' };
  var BOOLEAN_ATTRS = { 'aria-hidden': ['true', 'false'], 'aria-required': ['true', 'false'], 'aria-disabled': ['true', 'false'], 'aria-readonly': ['true', 'false'], 'aria-multiline': ['true', 'false'], 'aria-multiselectable': ['true', 'false'], 'aria-modal': ['true', 'false'], 'aria-atomic': ['true', 'false'], 'aria-busy': ['true', 'false'], 'aria-expanded': ['true', 'false', 'undefined'], 'aria-pressed': ['true', 'false', 'mixed', 'undefined'], 'aria-checked': ['true', 'false', 'mixed', 'undefined'], 'aria-selected': ['true', 'false', 'undefined'], 'aria-live': ['off', 'polite', 'assertive'], 'aria-haspopup': ['true', 'false', 'menu', 'listbox', 'tree', 'grid', 'dialog'], 'aria-orientation': ['horizontal', 'vertical', 'undefined'], 'aria-sort': ['none', 'ascending', 'descending', 'other'], 'aria-autocomplete': ['none', 'inline', 'list', 'both'], 'aria-invalid': ['true', 'false', 'grammar', 'spelling'], 'aria-current': ['page', 'step', 'location', 'date', 'time', 'true', 'false'] };

  function explicitRole(el) {
    var r = lower(el.getAttribute('role'));
    if (!r) return null;
    var tokens = r.split(/\s+/);
    for (var i = 0; i < tokens.length; i++) if (VALID_ROLES.indexOf(tokens[i]) >= 0) return tokens[i];
    return tokens[0];
  }

  function computedRole(el) {
    var r = explicitRole(el);
    if (r && VALID_ROLES.indexOf(r) >= 0) return r;
    if (el.nodeName === 'INPUT') {
      var t = lower(el.type);
      if (t === 'checkbox' || t === 'radio') return t;
      if (t === 'range') return 'slider';
      if (t === 'number') return 'spinbutton';
      if (t === 'button' || t === 'submit' || t === 'reset' || t === 'image') return 'button';
      if (t === 'search') return 'searchbox';
      if (t === 'hidden') return null;
      return el.hasAttribute('list') ? 'combobox' : 'textbox';
    }
    if (el.nodeName === 'A' && !el.hasAttribute('href')) return 'generic';
    if (el.nodeName === 'SECTION' && !el.hasAttribute('aria-label') && !el.hasAttribute('aria-labelledby')) return 'generic';
    if ((el.nodeName === 'HEADER' || el.nodeName === 'FOOTER') && el.closest('article, aside, main, nav, section')) return 'generic';
    if (el.nodeName === 'FORM' && !el.hasAttribute('aria-label') && !el.hasAttribute('aria-labelledby') && !el.hasAttribute('name')) return 'form';
    return IMPLICIT_ROLES[el.nodeName] || (GENERIC_TAGS.indexOf(el.nodeName) >= 0 ? 'generic' : null);
  }

  /* ISO 639-1 primary language subtags: the only two-letter codes the IANA registry contains. */
  var ISO_639_1 = ('aa ab ae af ak am an ar as av ay az ba be bg bh bi bm bn bo br bs ca ce ch co cr cs cu cv cy da de dv dz ee el en eo es et eu fa ff fi fj fo fr fy '
    + 'ga gd gl gn gu gv ha he hi ho hr ht hu hy hz ia id ie ig ii ik io is it iu ja jv ka kg ki kj kk kl km kn ko kr ks ku kv kw ky la lb lg li ln lo lt lu lv '
    + 'mg mh mi mk ml mn mr ms mt my na nb nd ne ng nl nn no nr nv ny oc oj om or os pa pi pl ps pt qu rm rn ro ru rw sa sc sd se sg si sk sl sm sn so sq sr ss '
    + 'st su sv sw ta te tg th ti tk tl tn to tr ts tt tw ty ug uk ur uz ve vi vo wa wo xh yi yo za zh zu').split(' ');

  /*
   * Three-letter codes for languages that also have a two-letter code (ISO 639-2/T and /B). The IANA
   * registry only lists the two-letter form for these, so the three-letter form is not a valid subtag.
   */
  var ISO_639_2_WITH_ALPHA2 = ('aar abk afr aka amh ara arg asm ava ave aym aze bak bam bel ben bih bis bod bos bre bul cat ces cha che chu chv cor cos cre cym '
    + 'dan deu div dzo ell eng epo est eus ewe fao fas fij fin fra fry ful gla gle glg glv grn guj hat hau heb her hin hmo hrv hun hye ibo ido iii iku ile ina '
    + 'ind ipk isl ita jav jpn kal kan kas kat kau kaz khm kik kin kir kom kon kor kua kur lao lat lav lim lin lit ltz lub lug mah mal mar mkd mlg mlt mon mri '
    + 'msa mya nau nav nbl nde ndo nep nld nno nob nor nya oci oji ori orm oss pan pli pol por pus que roh ron run rus sag san sin slk slv sme smo sna snd som '
    + 'sot spa sqi srd srp ssw sun swa swe tah tam tat tel tgk tgl tha tir ton tsn tso tuk tur twi uig ukr urd uzb ven vie vol wln wol xho yid yor zha zho zul '
    + 'alb arm baq bur chi cze dut fre geo ger gre ice mac mao may per rum scc scr slo tib wel').split(' ');

  /**
   * Validity of the primary language subtag. Returns 'valid', 'invalid', or 'unknown' for three-letter
   * codes that may legitimately be in the registry (languages with no two-letter form), where reporting
   * a failure would risk a false positive.
   */
  function langValidity(v) {
    var raw = String(v == null ? '' : v);
    if (norm(raw) === '') return 'invalid';
    var primary = norm(raw).split('-')[0].toLowerCase();
    if (!/^[a-z]{2,3}$/.test(primary)) return 'invalid';
    if (primary.length === 2) return ISO_639_1.indexOf(primary) >= 0 ? 'valid' : 'invalid';
    return ISO_639_2_WITH_ALPHA2.indexOf(primary) >= 0 ? 'invalid' : 'unknown';
  }

  function isValidLang(v) {
    return langValidity(v) !== 'invalid';
  }

  /*
   * Text that is rendered, or exposed to assistive technology even when visually hidden.
   * With `stopAtLang`, content governed by a nested lang declaration is excluded, because that
   * declaration—not the outer one—determines the language of that text.
   */
  function hasPerceivableContent(el, stopAtLang, depth) {
    if ((depth || 0) > 12 || !renderedForAT(el)) return false;
    for (var i = 0; i < el.childNodes.length; i++) {
      var n = el.childNodes[i];
      if (n.nodeType === 3) {
        if (norm(n.nodeValue)) return true;
        continue;
      }
      if (n.nodeType !== 1) continue;
      if (n.nodeName === 'SCRIPT' || n.nodeName === 'STYLE' || n.nodeName === 'TEMPLATE') continue;
      if (stopAtLang && n.hasAttribute('lang') && norm(n.getAttribute('lang'))) continue;
      if ((n.nodeName === 'IMG' || n.nodeName === 'AREA') && norm(n.getAttribute('alt') || '') && renderedForAT(n)) return true;
      if (hasPerceivableContent(n, stopAtLang, (depth || 0) + 1)) return true;
    }
    return false;
  }

  function renderedForAT(el) {
    var n = el;
    while (n && n.nodeType === 1) {
      var cs = style(n);
      if (!cs) return false;
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.visibility === 'collapse') return false;
      n = n.parentElement;
    }
    return true;
  }

  /* On screen: rendered and positioned within the document, not parked off-canvas. */
  function isOnScreen(el) {
    if (!isVisible(el)) return false;
    var r = el.getBoundingClientRect();
    var docW = Math.max(document.documentElement.scrollWidth, innerWidth);
    var docH = Math.max(document.documentElement.scrollHeight, innerHeight);
    return r.right > 0 && r.bottom > 0 && r.left < docW && r.top < docH;
  }

  /* --- colour contrast --------------------------------------------------- */

  function relLuminance(c) {
    function ch(v) { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }
    return 0.2126 * ch(c.r) + 0.7152 * ch(c.g) + 0.0722 * ch(c.b);
  }

  function contrastRatio(a, b) {
    var l1 = relLuminance(a), l2 = relLuminance(b);
    var hi = Math.max(l1, l2), lo = Math.min(l1, l2);
    return (hi + 0.05) / (lo + 0.05);
  }

  function over(top, bottom) {
    var a = top.a === undefined ? 1 : top.a;
    return { r: top.r * a + bottom.r * (1 - a), g: top.g * a + bottom.g * (1 - a), b: top.b * a + bottom.b * (1 - a), a: 1 };
  }

  function hex(c) {
    function h(v) { var s = Math.round(v).toString(16); return s.length < 2 ? '0' + s : s; }
    return '#' + h(c.r) + h(c.g) + h(c.b);
  }

  /* Walks up from el compositing background colours; bails out when a background image, gradient, opacity or filter makes the result unknowable. */
  function effectiveBackground(el) {
    var layers = [];
    var node = el;
    while (node && node.nodeType === 1) {
      var cs = style(node);
      if (!cs) break;
      if (cs.backgroundImage && cs.backgroundImage !== 'none') return { unknown: 'background-image on ' + node.nodeName.toLowerCase() };
      if (parseFloat(cs.opacity) < 1 && node !== el) return { unknown: 'opacity on ancestor' };
      if (cs.filter && cs.filter !== 'none' || cs.mixBlendMode && cs.mixBlendMode !== 'normal') return { unknown: 'filter/blend-mode' };
      var bg = parseColor(cs.backgroundColor);
      if (bg && bg.a > 0) {
        layers.push(bg);
        if (bg.a >= 1) break;
      }
      node = node.parentElement;
    }
    var result = { r: 255, g: 255, b: 255, a: 1 };
    for (var i = layers.length - 1; i >= 0; i--) result = over(layers[i], result);
    return { color: result };
  }

  function firstTextPoint(el) {
    for (var i = 0; i < el.childNodes.length; i++) {
      var n = el.childNodes[i];
      if (n.nodeType !== 3 || !norm(n.nodeValue)) continue;
      var range = document.createRange();
      range.selectNodeContents(n);
      var rects = range.getClientRects();
      if (rects.length) {
        var r = rects[0];
        return { x: r.left + Math.min(r.width / 2, 4), y: r.top + r.height / 2, height: r.height };
      }
    }
    return null;
  }

  /* Cumulative opacity applied to an element by itself and its ancestors. */
  function cumulativeOpacity(el) {
    var o = 1;
    var n = el;
    while (n && n.nodeType === 1) {
      var cs = style(n);
      if (!cs) break;
      var v = parseFloat(cs.opacity);
      if (!isNaN(v)) o *= v;
      n = n.parentElement;
    }
    return o;
  }

  /* Text that is only punctuation, symbols or box-drawing characters carries no information. */
  function isDecorativeText(s) {
    return !/[\p{L}\p{N}]/u.test(s);
  }

  /*
   * 1.4.3/1.4.6 exempt text that is part of an inactive user interface component. A label sits outside
   * the control it labels, so the control has to be found through the label relationship.
   */
  function labelsDisabledControl(el) {
    var label = el.closest('label');
    if (label) {
      var ctl = label.control || (label.getAttribute('for') ? document.getElementById(label.getAttribute('for')) : label.querySelector('input, select, textarea, button'));
      if (ctl && (ctl.disabled || lower(ctl.getAttribute('aria-disabled')) === 'true')) return true;
    }
    var ids = [];
    var n = el;
    while (n && n.nodeType === 1) { if (n.id) ids.push(n.id); n = n.parentElement; }
    for (var i = 0; i < ids.length; i++) {
      var refs = document.querySelectorAll('[aria-labelledby~="' + esc(ids[i]) + '"], [aria-describedby~="' + esc(ids[i]) + '"]');
      for (var j = 0; j < refs.length; j++) {
        if (refs[j].disabled || lower(refs[j].getAttribute('aria-disabled')) === 'true') return true;
      }
    }
    return false;
  }

  /* Every element with its own text, including inside shadow roots. */
  function textBearingElements() {
    var out = [];
    function walk(root, depth) {
      if (depth > 12) return;
      var all = root.querySelectorAll('*');
      for (var i = 0; i < all.length; i++) {
        var el = all[i];
        if (hasOwnText(el)) out.push(el);
        if (el.shadowRoot) walk(el.shadowRoot, depth + 1);
      }
    }
    walk(document.body || document, 0);
    return out;
  }

  function colorContrast(levelAAA) {
    var out = [];
    var all = textBearingElements();
    var checked = 0;
    var unmeasurable = [];
    var seenKeys = {};
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      if (el.nodeName === 'SCRIPT' || el.nodeName === 'STYLE' || el.nodeName === 'NOSCRIPT' || el.nodeName === 'OPTION' || el.nodeName === 'TITLE') continue;
      if (!isOnScreen(el) || !inAccessibilityTree(el)) continue;
      if (el.closest('[disabled], [aria-disabled="true"], option, select')) continue;
      if (labelsDisabledControl(el)) continue;
      if (isDecorativeText(ownText(el))) continue;
      var cs = style(el);
      if (!cs) continue;
      var fg = parseColor(cs.color);
      if (!fg || fg.a === 0) continue;
      var fontSize = parseFloat(cs.fontSize);
      if (!fontSize || fontSize < 1) continue;
      if (cs.webkitTextFillColor && parseColor(cs.webkitTextFillColor) && cs.webkitTextFillColor !== cs.color) fg = parseColor(cs.webkitTextFillColor);
      var weight = parseInt(cs.fontWeight, 10) || (cs.fontWeight === 'bold' ? 700 : 400);
      var large = fontSize >= 24 || (fontSize >= 18.66 && weight >= 700);
      var required = levelAAA ? (large ? 4.5 : 7) : (large ? 3 : 4.5);
      var reason = null;
      if (cs.textShadow && cs.textShadow !== 'none') reason = 'text-shadow';
      var bgInfo = effectiveBackground(el);
      if (!reason && bgInfo.unknown) reason = bgInfo.unknown;
      var pt = firstTextPoint(el);
      if (!reason && pt && pt.x >= 0 && pt.y >= 0 && pt.x < innerWidth && pt.y < innerHeight) {
        var top = document.elementFromPoint(pt.x, pt.y);
        if (top && top !== el && !el.contains(top) && !top.contains(el)) reason = 'content painted over the text';
      }
      if (reason) {
        // hand the element to the screenshot sampler instead of guessing
        unmeasurable.push({ selector: cssPath(el), html: snippet(el), rect: pt ? { x: pt.x, y: pt.y, width: 1, height: pt.height } : rect(el),
          reason: reason, foreground: hex(fg), fontSize: Math.round(fontSize * 10) / 10, largeText: large, required: required, opacity: cumulativeOpacity(el) });
        continue;
      }
      var opacity = cumulativeOpacity(el);
      var fgEffective = fg.a < 1 || opacity < 1 ? over({ r: fg.r, g: fg.g, b: fg.b, a: (fg.a === undefined ? 1 : fg.a) * opacity }, bgInfo.color) : fg;
      var ratio = contrastRatio(fgEffective, bgInfo.color);
      checked++;
      var data = { ratio: Math.round(ratio * 100) / 100, required: required, foreground: hex(fgEffective), background: hex(bgInfo.color), fontSize: Math.round(fontSize * 10) / 10, fontWeight: weight, largeText: large, opacity: Math.round(opacity * 100) / 100, text: visibleText(el).slice(0, 80) };
      if (ratio + 0.005 < required) {
        var key = data.foreground + data.background + (large ? 'L' : 'S');
        seenKeys[key] = (seenKeys[key] || 0) + 1;
        if (seenKeys[key] <= 8) {
          out.push(fc('failed', el, 'contrast', 'Text contrast ' + data.ratio + ':1 is below the ' + required + ':1 ' + (levelAAA ? 'AAA' : 'AA') + ' minimum (' + data.foreground + ' on ' + data.background + ', ' + data.fontSize + 'px' + (large ? ' large text' : '') + ').', data));
        }
      }
    }
    var suppressed = 0;
    Object.keys(seenKeys).forEach(function (k) { if (seenKeys[k] > 8) suppressed += seenKeys[k] - 8; });
    if (suppressed > 0) out.push(fc('failed', null, 'contrast', suppressed + ' further element(s) with the same failing colour pairs not listed individually.', { suppressed: suppressed, checked: checked }));
    if (out.length === 0 && checked > 0) out.push(fc('passed', null, 'contrast', checked + ' text element(s) meet the ' + (levelAAA ? 'AAA' : 'AA') + ' contrast minimum.', { checked: checked }));
    // reported separately so the driver can measure these from a screenshot
    if (unmeasurable.length) out.push(fc('cantTell', null, 'contrast-unmeasurable', unmeasurable.length + ' text element(s) need pixel sampling (' + unmeasurable[0].reason + ').', { targets: unmeasurable, levelAAA: !!levelAAA }));
    return out;
  }

  rules['color-contrast'] = function () { return colorContrast(false); };
  rules['color-contrast-enhanced'] = function () { return colorContrast(true); };

  /* --- text alternatives and names --------------------------------------- */

  /* Findings carry a `check` sub-identifier so a rule can cover several ACT rules while staying addressable. */
  function fc(outcome, el, check, message, data) {
    data = data || {};
    data.check = check;
    return finding(outcome, el, message, data);
  }

  /* Rendered in the accessibility tree (approximation of "included in the accessibility tree"). */
  function inAccessibilityTree(el) {
    if (isHiddenFromAT(el)) return false;
    var n = el;
    while (n && n.nodeType === 1) {
      var cs = style(n);
      if (!cs) return false;
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.visibility === 'collapse') return false;
      n = n.parentElement;
    }
    return true;
  }

  rules['image-alt'] = function () {
    var out = [];
    document.querySelectorAll('img, input[type="image"], area[href], svg, object, [role="img"], [role="image"]').forEach(function (el) {
      var tag = el.nodeName.toUpperCase();
      var role = explicitRole(el);
      if (role === 'presentation' || role === 'none') return; // aria-validity#decorative-not-exposed covers these
      // an image marked decorative with alt="" but named through ARIA is exposed after all
      if (tag === 'IMG' && el.getAttribute('alt') === '' && norm(el.getAttribute('aria-label') || el.getAttribute('aria-labelledby') || '')) {
        out.push(fc('failed', el, 'decorative-not-exposed', 'Image is marked decorative with alt="" but is named through '
                + (el.hasAttribute('aria-label') ? 'aria-label' : 'aria-labelledby') + ', so it is still exposed to assistive technology.', { alt: '' }));
      }
      if (!inAccessibilityTree(el)) return;
      var name = norm(accessibleName(el));
      var data = { alt: el.getAttribute('alt'), src: (el.currentSrc || el.getAttribute('src') || el.getAttribute('data') || '').slice(0, 200) };
      if (tag === 'SVG') {
        if (!(role === 'img' || role === 'image' || /^graphics-/.test(role || ''))) return; // no explicit img role: out of scope here
        if (name) out.push(fc('passed', el, 'svg-name', 'SVG with role="' + role + '" is named "' + name + '".', data));
        else out.push(fc('failed', el, 'svg-name', 'SVG element with role="' + role + '" has no accessible name. Add <title>, aria-label or aria-labelledby.', data));
        return;
      }
      if (tag === 'OBJECT') {
        // an object takes its name only from the author; its fallback content is not a name
        var objName = norm(el.getAttribute('aria-label') || '');
        var lb = el.getAttribute('aria-labelledby');
        if (!objName && lb) {
          objName = norm(lb.split(/\s+/).map(function (id) { var ref = document.getElementById(id); return ref ? textFromSubtree(ref, 0) : ''; }).join(' '));
        }
        if (!objName) objName = norm(el.getAttribute('title') || '');
        data.name = objName;
        if (objName) out.push(fc('passed', el, 'object-name', 'Object is named "' + objName + '".', data));
        else out.push(fc('failed', el, 'object-name', '<object> rendering non-text content has no accessible name. Add aria-label, aria-labelledby or a title attribute (fallback content inside the element does not provide a name).', data));
        return;
      }
      if (tag === 'IMG' || tag === 'AREA' || tag === 'INPUT') {
        var check = tag === 'INPUT' ? 'image-button-name' : 'image-name';
        if (el.hasAttribute('alt') && (tag !== 'INPUT' || norm(el.getAttribute('alt')))) { out.push(fc('passed', el, check, 'Has alt text.', data)); return; }
        if (name) { out.push(fc('passed', el, check, 'Named "' + name + '".', data)); return; }
        out.push(fc('failed', el, check, '<' + tag.toLowerCase() + (tag === 'INPUT' ? ' type="image"' : '') + '> has no'
                + (tag === 'INPUT' ? ' accessible name' : ' alt attribute and no other text alternative')
                + '. Add alt="…"' + (tag === 'INPUT' ? '' : ' (or alt="" if purely decorative)') + '.', data));
        return;
      }
      if (name) out.push(fc('passed', el, 'role-img-name', 'role="img" element is named.', data));
      else out.push(fc('failed', el, 'role-img-name', 'Element with role="img" has no accessible name.', data));
    });
    return out;
  };

  var NAME_CHECK_BY_ROLE = { button: 'button-name', link: 'link-name', menuitem: 'menuitem-name', menuitemcheckbox: 'menuitem-name', menuitemradio: 'menuitem-name' };
  var FIELD_ROLES = ['textbox', 'searchbox', 'combobox', 'listbox', 'slider', 'spinbutton', 'checkbox', 'radio', 'switch'];

  rules['control-name'] = function () {
    var out = [];
    var sel = 'a[href], area[href], button, input:not([type="hidden"]), select, textarea, summary, [role="button"], [role="link"], [role="checkbox"], [role="radio"], [role="switch"], [role="tab"], [role="menuitem"], [role="menuitemcheckbox"], [role="menuitemradio"], [role="textbox"], [role="searchbox"], [role="combobox"], [role="listbox"], [role="slider"], [role="spinbutton"], [role="option"], [role="treeitem"], [role="dialog"], [role="alertdialog"], [role="progressbar"], iframe';
    document.querySelectorAll(sel).forEach(function (el) {
      if (isHiddenFromAT(el)) return;
      if (el.nodeName === 'INPUT' && lower(el.type) === 'image') return; // covered by image-alt#image-button-name
      var visible = isVisible(el);
      var tabbable = isTabbable(el);
      if (el.nodeName === 'IFRAME') {
        if (!inAccessibilityTree(el)) return;
        var t = norm(accessibleName(el));
        if (!t) out.push(fc('failed', el, 'iframe-name', '<iframe> has no accessible name; screen reader users cannot tell what the frame contains. Add a title attribute.', { src: (el.getAttribute('src') || '').slice(0, 200) }));
        else out.push(fc('passed', el, 'iframe-name', 'Frame is named "' + t + '".', { name: t }));
        return;
      }
      if (!visible && !tabbable) return;
      var name = norm(accessibleName(el));
      var role = computedRole(el) || el.nodeName.toLowerCase();
      if (el.nodeName === 'SUMMARY' && !(el.parentElement && el.parentElement.nodeName === 'DETAILS')) return;
      var check = el.nodeName === 'SUMMARY' ? 'summary-name'
              : NAME_CHECK_BY_ROLE[role] ? NAME_CHECK_BY_ROLE[role]
              : FIELD_ROLES.indexOf(role) >= 0 ? 'field-name'
              : 'other-name';
      var data = { role: role, name: name, tag: el.nodeName.toLowerCase(), visible: visible };
      if (el.nodeName === 'SUMMARY' && !name) {
        // a summary with no author name still exposes the UA default label ("Details")
        out.push(fc('failed', el, check, '<summary> has no accessible name; it is announced with the browser default label only.', data));
        return;
      }
      if (name) { out.push(fc('passed', el, check, 'Accessible name: "' + name.slice(0, 80) + '".', data)); return; }
      var what = el.nodeName === 'A' || role === 'link' ? 'Link' : role === 'button' ? 'Button' : FIELD_ROLES.indexOf(role) >= 0 ? 'Form field' : 'Control (role=' + role + ')';
      var hint = role === 'link' || role === 'button' ? 'Add visible text, an aria-label, or alt text on the contained image.' : FIELD_ROLES.indexOf(role) >= 0 ? 'Associate a <label for>, wrap it in a <label>, or use aria-label/aria-labelledby.' : 'Provide aria-label or aria-labelledby.';
      out.push(fc('failed', el, check, what + ' has no accessible name' + (visible ? '' : ' and is in the tab order while visually hidden') + '. ' + hint, data));
    });
    return out;
  };

  /* --- ARIA validity ------------------------------------------------------ */

  /* Roles whose children are presentational: descendants must not be focusable (ACT 307n5z). */
  var PRESENTATIONAL_CHILDREN_ROLES = ['button', 'checkbox', 'img', 'image', 'math', 'menuitemcheckbox', 'menuitemradio', 'meter', 'option', 'progressbar', 'radio', 'scrollbar', 'separator', 'slider', 'switch', 'tab'];

  /* Roles that require particular owned roles (ACT bc4a75). */
  var REQUIRED_OWNED = {
    list: ['listitem'],
    feed: ['article'],
    grid: ['row', 'rowgroup'],
    rowgroup: ['row'],
    row: ['cell', 'gridcell', 'columnheader', 'rowheader'],
    listbox: ['option', 'group'],
    menu: ['menuitem', 'menuitemcheckbox', 'menuitemradio', 'group', 'separator'],
    menubar: ['menuitem', 'menuitemcheckbox', 'menuitemradio', 'group', 'separator'],
    radiogroup: ['radio'],
    tablist: ['tab'],
    table: ['row', 'rowgroup'],
    tree: ['treeitem', 'group'],
    treegrid: ['row', 'rowgroup']
  };

  /* Non-global ARIA attributes supported per role (ACT 5c01ea). Roles absent from the table are not judged. */
  var ROLE_SUPPORTED = {
    alert: [], alertdialog: ['aria-modal'], application: ['aria-activedescendant', 'aria-expanded'],
    article: ['aria-posinset', 'aria-setsize', 'aria-expanded'],
    button: ['aria-expanded', 'aria-pressed', 'aria-disabled', 'aria-haspopup'],
    cell: ['aria-colindex', 'aria-colspan', 'aria-rowindex', 'aria-rowspan', 'aria-colindextext', 'aria-rowindextext'],
    checkbox: ['aria-checked', 'aria-readonly', 'aria-required', 'aria-disabled', 'aria-expanded', 'aria-invalid', 'aria-errormessage'],
    columnheader: ['aria-sort', 'aria-colindex', 'aria-colspan', 'aria-rowindex', 'aria-rowspan', 'aria-expanded', 'aria-selected', 'aria-readonly', 'aria-required', 'aria-disabled', 'aria-colindextext', 'aria-rowindextext'],
    combobox: ['aria-expanded', 'aria-controls', 'aria-autocomplete', 'aria-readonly', 'aria-required', 'aria-activedescendant', 'aria-disabled', 'aria-invalid', 'aria-errormessage', 'aria-haspopup'],
    complementary: [], contentinfo: [], definition: [], dialog: ['aria-modal'], directory: [],
    document: [], feed: [], figure: [], form: [], generic: [],
    grid: ['aria-multiselectable', 'aria-readonly', 'aria-colcount', 'aria-rowcount', 'aria-disabled'],
    gridcell: ['aria-readonly', 'aria-required', 'aria-selected', 'aria-colindex', 'aria-colspan', 'aria-rowindex', 'aria-rowspan', 'aria-disabled', 'aria-expanded', 'aria-invalid', 'aria-errormessage', 'aria-colindextext', 'aria-rowindextext'],
    group: ['aria-activedescendant', 'aria-disabled'],
    heading: ['aria-level'], img: [], image: [], link: ['aria-disabled', 'aria-expanded', 'aria-haspopup'],
    list: [], listbox: ['aria-multiselectable', 'aria-readonly', 'aria-required', 'aria-activedescendant', 'aria-disabled', 'aria-expanded', 'aria-invalid', 'aria-errormessage', 'aria-orientation'],
    listitem: ['aria-level', 'aria-posinset', 'aria-setsize'],
    log: [], main: [], marquee: [], math: [],
    menu: ['aria-activedescendant', 'aria-disabled', 'aria-orientation'],
    menubar: ['aria-activedescendant', 'aria-disabled', 'aria-orientation'],
    menuitem: ['aria-disabled', 'aria-expanded', 'aria-haspopup', 'aria-posinset', 'aria-setsize'],
    menuitemcheckbox: ['aria-checked', 'aria-disabled', 'aria-haspopup', 'aria-posinset', 'aria-setsize'],
    menuitemradio: ['aria-checked', 'aria-disabled', 'aria-haspopup', 'aria-posinset', 'aria-setsize'],
    meter: ['aria-valuemax', 'aria-valuemin', 'aria-valuenow', 'aria-valuetext'],
    navigation: [], note: [],
    option: ['aria-selected', 'aria-checked', 'aria-posinset', 'aria-setsize', 'aria-disabled'],
    paragraph: [], presentation: [], none: [],
    progressbar: ['aria-valuemax', 'aria-valuemin', 'aria-valuenow', 'aria-valuetext'],
    radio: ['aria-checked', 'aria-disabled', 'aria-posinset', 'aria-setsize'],
    radiogroup: ['aria-readonly', 'aria-required', 'aria-activedescendant', 'aria-disabled', 'aria-invalid', 'aria-errormessage', 'aria-orientation'],
    region: [], row: ['aria-colindex', 'aria-level', 'aria-rowindex', 'aria-selected', 'aria-setsize', 'aria-posinset', 'aria-expanded', 'aria-disabled'],
    rowgroup: [], rowheader: ['aria-sort', 'aria-colindex', 'aria-colspan', 'aria-rowindex', 'aria-rowspan', 'aria-expanded', 'aria-selected', 'aria-readonly', 'aria-required', 'aria-disabled', 'aria-colindextext', 'aria-rowindextext'],
    scrollbar: ['aria-controls', 'aria-valuemax', 'aria-valuemin', 'aria-valuenow', 'aria-valuetext', 'aria-orientation', 'aria-disabled'],
    search: [], searchbox: ['aria-activedescendant', 'aria-autocomplete', 'aria-multiline', 'aria-placeholder', 'aria-readonly', 'aria-required', 'aria-disabled', 'aria-invalid', 'aria-errormessage', 'aria-haspopup'],
    separator: ['aria-valuemax', 'aria-valuemin', 'aria-valuenow', 'aria-valuetext', 'aria-orientation', 'aria-disabled'],
    slider: ['aria-valuemax', 'aria-valuemin', 'aria-valuenow', 'aria-valuetext', 'aria-orientation', 'aria-readonly', 'aria-required', 'aria-disabled', 'aria-invalid', 'aria-errormessage', 'aria-haspopup'],
    spinbutton: ['aria-valuemax', 'aria-valuemin', 'aria-valuenow', 'aria-valuetext', 'aria-readonly', 'aria-required', 'aria-activedescendant', 'aria-disabled', 'aria-invalid', 'aria-errormessage'],
    status: [], strong: [], switch: ['aria-checked', 'aria-readonly', 'aria-required', 'aria-disabled', 'aria-expanded', 'aria-invalid', 'aria-errormessage'],
    tab: ['aria-selected', 'aria-expanded', 'aria-posinset', 'aria-setsize', 'aria-disabled', 'aria-haspopup'],
    table: ['aria-colcount', 'aria-rowcount'],
    tablist: ['aria-multiselectable', 'aria-orientation', 'aria-activedescendant', 'aria-disabled'],
    tabpanel: [], term: [],
    textbox: ['aria-activedescendant', 'aria-autocomplete', 'aria-multiline', 'aria-placeholder', 'aria-readonly', 'aria-required', 'aria-disabled', 'aria-invalid', 'aria-errormessage', 'aria-haspopup'],
    timer: [], toolbar: ['aria-activedescendant', 'aria-orientation', 'aria-disabled'], tooltip: [],
    tree: ['aria-multiselectable', 'aria-required', 'aria-activedescendant', 'aria-disabled', 'aria-invalid', 'aria-errormessage', 'aria-orientation'],
    treegrid: ['aria-multiselectable', 'aria-readonly', 'aria-required', 'aria-activedescendant', 'aria-colcount', 'aria-rowcount', 'aria-disabled', 'aria-invalid', 'aria-errormessage', 'aria-orientation'],
    treeitem: ['aria-checked', 'aria-expanded', 'aria-level', 'aria-posinset', 'aria-setsize', 'aria-selected', 'aria-disabled', 'aria-haspopup'],
    audio: [], video: []
  };

  /* Walks up from a group/rowgroup to see whether it is itself inside one of the required contexts. */
  function isInRequiredContext(el, ctx) {
    var p = el.parentElement, hops = 0;
    while (p && hops++ < 8) {
      var r = ownedRoleOf(p);
      if (r === 'presentation' || r === 'none') { p = p.parentElement; continue; }
      if (r === 'group' || r === 'rowgroup') { p = p.parentElement; continue; }
      return ctx.indexOf(r) >= 0;
    }
    return false;
  }

  function ownedRoleOf(el) {
    var r = explicitRole(el);
    if (r && VALID_ROLES.indexOf(r) >= 0) return r;
    return IMPLICIT_ROLES[el.nodeName] || null;
  }

  /*
   * Roles of the elements owned by el. Only an explicit presentation/none role is transparent; a
   * generic element (a plain div or span) owns its own children, which is why a listitem inside a div
   * inside a list is not owned by the list.
   * `group`/`rowgroup` containers are recursed into because ARIA allows them to carry required owned
   * elements on behalf of their parent.
   */
  function ownedRoles(el, depth, groupRoles) {
    var roles = [];
    var kids = Array.prototype.slice.call(el.children);
    var owns = el.getAttribute('aria-owns');
    if (owns) owns.split(/\s+/).forEach(function (id) { var o = document.getElementById(id); if (o) kids.push(o); });
    kids.forEach(function (c) {
      if (c.nodeName === 'SCRIPT' || c.nodeName === 'STYLE' || c.nodeName === 'TEMPLATE') return;
      if (lower(c.getAttribute('aria-hidden')) === 'true') return;
      if (!renderedForAT(c)) return;
      var r = ownedRoleOf(c);
      if (r === 'presentation' || r === 'none') {
        if (depth < 5) roles = roles.concat(ownedRoles(c, depth + 1, groupRoles));
        return;
      }
      if ((r === 'group' || r === 'rowgroup') && groupRoles) {
        roles.push(r);
        if (depth < 5) roles = roles.concat(ownedRoles(c, depth + 1, groupRoles));
        return;
      }
      roles.push(r === null ? 'generic' : r);
    });
    return roles;
  }

  rules['aria-validity'] = function () {
    var out = [];
    document.querySelectorAll('*').forEach(function (el) {
      if (el.nodeName === 'SCRIPT' || el.nodeName === 'STYLE' || el.nodeName === 'TEMPLATE') return;
      var roleAttr = el.getAttribute('role');
      var ariaAttrs = Array.prototype.filter.call(el.attributes, function (a) { return a.name.indexOf('aria-') === 0; });
      if (roleAttr === null && ariaAttrs.length === 0) return;
      var exposed = inAccessibilityTree(el);
      var role = explicitRole(el);

      /*
       * Presentational Roles Conflict Resolution: an element marked decorative is still exposed when it
       * is focusable or carries a global ARIA property, so the decorative marking is a lie.
       */
      if (role === 'presentation' || role === 'none') {
        var exposingAttrs = Array.prototype.filter.call(el.attributes, function (a) {
          var n = a.name.toLowerCase();
          return GLOBAL_ARIA.indexOf(n) >= 0 && n !== 'aria-hidden' && norm(a.value) !== '';
        });
        if (isFocusable(el) || exposingAttrs.length) {
          out.push(fc('failed', el, 'decorative-not-exposed', '<' + el.nodeName.toLowerCase() + '> is marked decorative with role="' + role + '" but is still exposed ('
                  + (isFocusable(el) ? 'it is focusable' : 'it carries ' + exposingAttrs[0].name) + '), so browsers ignore the presentational role and it keeps its native semantics.', { role: role }));
        } else if (exposed) {
          out.push(fc('passed', el, 'decorative-not-exposed', 'role="' + role + '" removes the element from the accessibility tree as intended.', { role: role }));
        }
      }

      if (roleAttr !== null && norm(roleAttr)) {
        var tokens = norm(roleAttr).split(/\s+/);
        var anyValid = tokens.some(function (t) { return VALID_ROLES.indexOf(lower(t)) >= 0; });
        if (!anyValid) {
          out.push(fc('failed', el, 'role-valid', 'role="' + roleAttr + '" contains no valid WAI-ARIA role; assistive technology falls back to the native semantics.', { role: roleAttr }));
        } else {
          out.push(fc('passed', el, 'role-valid', 'role="' + roleAttr + '" resolves to the valid role "' + role + '".', { role: role }));
        }
      }

      var effectiveRole = role && VALID_ROLES.indexOf(role) >= 0 ? role : null;
      if (exposed && effectiveRole) {
        var req = REQUIRED_ATTRS[role];
        // a separator only reports a value when it is focusable; option/select state comes from the host language
        if (role === 'separator' && !isFocusable(el)) req = null;
        if (role === 'option' && el.nodeName === 'OPTION') req = null;
        if (req && !(el.nodeName === 'SELECT' || /^H[1-6]$/.test(el.nodeName) || el.nodeName === 'METER' || el.nodeName === 'PROGRESS'
                || (el.nodeName === 'INPUT' && ['checkbox', 'radio', 'range'].indexOf(lower(el.type)) >= 0))) {
          var missing = req.filter(function (a) { return !el.hasAttribute(a); });
          if (missing.length) out.push(fc('failed', el, 'role-required-attrs', 'role="' + role + '" requires ' + missing.join(', ') + ' so that assistive technology can report its state.', { role: role, missing: missing }));
          else out.push(fc('passed', el, 'role-required-attrs', 'role="' + role + '" has its required states and properties.', { role: role }));
        }
        var ctx = REQUIRED_CONTEXT[role];
        if (ctx) {
          // the owner is the nearest ancestor that is not explicitly presentational
          var p = el.parentElement, ok = false, hops = 0;
          while (p && hops++ < 8) {
            var pr = ownedRoleOf(p);
            if (pr === 'presentation' || pr === 'none') { p = p.parentElement; continue; }
            ok = ctx.indexOf(pr) >= 0 || (pr === 'rowgroup' && ctx.indexOf('table') >= 0)
                    || ((pr === 'group' || pr === 'rowgroup') && isInRequiredContext(p, ctx));
            break;
          }
          var ownedBy = el.id ? document.querySelector('[aria-owns~="' + esc(el.id) + '"]') : null;
          if (!ok && ownedBy) ok = ctx.indexOf(ownedRoleOf(ownedBy)) >= 0;
          if (!ok) out.push(fc('failed', el, 'role-required-context', 'role="' + role + '" must be owned by an element with role ' + ctx.join(' or ') + '; its nearest non-presentational ancestor is "' + (p ? ownedRoleOf(p) || 'generic' : 'none') + '".', { role: role, requiredContext: ctx }));
          else out.push(fc('passed', el, 'role-required-context', 'role="' + role + '" is in a valid context.', { role: role }));
        }
      }
      var ownerRole = effectiveRole || (roleAttr === null ? IMPLICIT_ROLES[el.nodeName] : null);
      if (exposed && ownerRole) {
        var needOwned = REQUIRED_OWNED[ownerRole];
        if (needOwned) {
          var owned = ownedRoles(el, 0, true);
          var permitted = needOwned.concat(['group', 'rowgroup']);
          var bad = owned.filter(function (r) { return permitted.indexOf(r) < 0; });
          if (bad.length) {
            out.push(fc('failed', el, 'role-required-owned', 'role="' + ownerRole + '" owns element(s) exposed as ' + bad.filter(function (v, i, a) { return a.indexOf(v) === i; }).join(', ') + '; it must own only ' + needOwned.join(', ') + '.', { role: ownerRole, owned: owned, required: needOwned }));
          } else if (!owned.length) {
            out.push(fc('failed', el, 'role-required-owned', 'role="' + ownerRole + '" owns no ' + needOwned.join('/') + ' element; assistive technology reports an empty ' + ownerRole + '.', { role: ownerRole, required: needOwned }));
          } else {
            out.push(fc('passed', el, 'role-required-owned', 'role="' + ownerRole + '" owns only permitted roles.', { role: ownerRole, owned: owned }));
          }
        }
        if (PRESENTATIONAL_CHILDREN_ROLES.indexOf(ownerRole) >= 0) {
          var inner = Array.prototype.filter.call(el.querySelectorAll('*'), isTabbable);
          if (inner.length) out.push(fc('failed', el, 'presentational-children', 'role="' + ownerRole + '" makes its children presentational, but the subtree contains ' + inner.length + ' focusable element(s) (e.g. ' + cssPath(inner[0]) + '). Assistive technology cannot reach or describe them.', { role: ownerRole, focusable: inner.slice(0, 3).map(cssPath) }));
          else out.push(fc('passed', el, 'presentational-children', 'role="' + ownerRole + '" has no focusable content.', { role: ownerRole }));
        }
      }

      /*
       * Presentational Roles Conflict Resolution: role="none"/"presentation" is ignored when the element
       * is focusable or carries a global ARIA property, so the native role is what assistive technology
       * actually exposes.
       */
      var effRole = role && VALID_ROLES.indexOf(role) >= 0 ? role : computedRole(el);
      if ((effRole === 'presentation' || effRole === 'none')
              && (isFocusable(el) || ariaAttrs.some(function (a) { return GLOBAL_ARIA.indexOf(a.name.toLowerCase()) >= 0 && a.name.toLowerCase() !== 'aria-hidden'; }))) {
        effRole = IMPLICIT_ROLES[el.nodeName] || 'generic';
      }
      ariaAttrs.forEach(function (a) {
        var an = a.name.toLowerCase();
        if (VALID_ARIA.indexOf(an) < 0) {
          out.push(fc('failed', el, 'aria-attr-defined', a.name + ' is not defined in WAI-ARIA.', { attribute: a.name }));
          return;
        }
        out.push(fc('passed', el, 'aria-attr-defined', a.name + ' is defined in WAI-ARIA.', { attribute: a.name }));
        var value = norm(a.value);
        // an empty value means the attribute is not set to anything, so there is nothing to validate
        if (value === '' && an !== 'aria-label' && an !== 'aria-roledescription' && an !== 'aria-placeholder' && an !== 'aria-valuetext' && an !== 'aria-description') return;
        if (!exposed) return; // an unexposed element conveys nothing either way
        if (an === 'aria-relevant') {
          var relevantOk = value.split(/\s+/).every(function (t) { return ['additions', 'removals', 'text', 'all'].indexOf(t) >= 0; });
          if (!relevantOk) out.push(fc('failed', el, 'aria-attr-value', a.name + '="' + a.value + '" must be a space-separated list of additions, removals, text or all.', { attribute: a.name, value: a.value }));
          else out.push(fc('passed', el, 'aria-attr-value', a.name + ' has an allowed value.', { attribute: a.name }));
          return;
        }
        var allowed = BOOLEAN_ATTRS[an];
        if (allowed) {
          if (allowed.indexOf(lower(value)) < 0) out.push(fc('failed', el, 'aria-attr-value', a.name + '="' + a.value + '" is not an allowed value (' + allowed.join(' | ') + ').', { attribute: a.name, value: a.value }));
          else out.push(fc('passed', el, 'aria-attr-value', a.name + ' has an allowed value.', { attribute: a.name }));
        } else if (an === 'aria-level' || an === 'aria-colcount' || an === 'aria-rowcount' || an === 'aria-colindex' || an === 'aria-rowindex' || an === 'aria-colspan' || an === 'aria-rowspan' || an === 'aria-setsize' || an === 'aria-posinset') {
          var min = (an === 'aria-colcount' || an === 'aria-rowcount' || an === 'aria-setsize') ? -1 : (an === 'aria-colspan' || an === 'aria-rowspan') ? 0 : 1;
          var n = value === '' ? NaN : Number(value);
          if (!(Number.isInteger(n) && n >= min)) out.push(fc('failed', el, 'aria-attr-value', a.name + '="' + a.value + '" must be an integer' + (min === 1 ? ' of 1 or more' : min === -1 ? ' (-1 or more)' : ' of 0 or more') + '.', { attribute: a.name, value: a.value }));
          else out.push(fc('passed', el, 'aria-attr-value', a.name + ' has an allowed value.', { attribute: a.name }));
        } else if (an === 'aria-valuenow' || an === 'aria-valuemin' || an === 'aria-valuemax') {
          if (value === '' || isNaN(Number(value))) out.push(fc('failed', el, 'aria-attr-value', a.name + '="' + a.value + '" must be a number.', { attribute: a.name, value: a.value }));
          else out.push(fc('passed', el, 'aria-attr-value', a.name + ' has an allowed value.', { attribute: a.name }));
        } else if (IDREF_ATTRS.indexOf(an) >= 0) {
          var ids = value.split(/\s+/).filter(Boolean);
          var missingIds = ids.filter(function (id) { return !document.getElementById(id); });
          if (ID_ATTRS_SINGLE[an] && ids.length > 1) {
            out.push(fc('failed', el, 'aria-attr-value', a.name + ' accepts a single id reference but lists ' + ids.length + '.', { attribute: a.name, value: a.value }));
          } else if (missingIds.length && missingIds.length === ids.length) {
            var strict = ID_ATTRS_STRICT[an] || (an === 'aria-controls' && lower(el.getAttribute('aria-expanded')) !== 'false');
            out.push(fc(strict ? 'failed' : 'needsReview', el, 'idref-missing', a.name + ' references id(s) that do not exist: ' + missingIds.join(', ') + '.', { attribute: a.name, missing: missingIds }));
          }
        }
        if (GLOBAL_ARIA.indexOf(an) < 0 && effRole && ROLE_SUPPORTED[effRole]) {
          if (ROLE_SUPPORTED[effRole].indexOf(an) < 0) {
            out.push(fc('failed', el, 'aria-attr-permitted', a.name + ' is not supported by role "' + effRole + '"; assistive technology ignores it.', { attribute: a.name, role: effRole }));
          } else {
            out.push(fc('passed', el, 'aria-attr-permitted', a.name + ' is supported by role "' + effRole + '".', { attribute: a.name, role: effRole }));
          }
        }
        var namingAttrs = ['aria-label', 'aria-labelledby', 'aria-braillelabel'];
        if (GLOBAL_ARIA.indexOf(an) >= 0 && an !== 'aria-hidden' && (role === 'presentation' || role === 'none')) {
          out.push(fc('failed', el, 'aria-prohibited', a.name + ' is a global ARIA property, so it makes role="' + role + '" invalid; the element keeps its native semantics instead of being presentational.', { attribute: a.name, role: role }));
        } else if (namingAttrs.indexOf(an) >= 0 && effRole && NAME_PROHIBITED_ROLES.indexOf(effRole) >= 0 && norm(a.value)) {
          out.push(fc('failed', el, 'aria-prohibited', a.name + ' is prohibited on <' + el.nodeName.toLowerCase() + '> with role "' + effRole + '"; screen readers ignore or misreport it. Give the element an appropriate role (e.g. group, region, img) or move the label to a real control.', { role: effRole, attribute: an }));
        } else if ((an === 'aria-roledescription' || an === 'aria-brailleroledescription') && norm(a.value)
                && (!effRole || effRole === 'generic' || effRole === 'presentation' || effRole === 'none' || NAME_PROHIBITED_ROLES.indexOf(effRole) >= 0)) {
          out.push(fc('failed', el, 'aria-prohibited', a.name + ' requires a role with semantics; on <' + el.nodeName.toLowerCase() + '> (role "' + (effRole || 'generic') + '") assistive technology has no role to describe, so it is ignored.', { role: effRole, attribute: an }));
        }
      });

      if (lower(el.getAttribute('aria-hidden')) === 'true') {
        if (el === document.body || el === document.documentElement) {
          out.push(fc('failed', el, 'aria-hidden-focusable', 'aria-hidden="true" on <' + el.nodeName.toLowerCase() + '> hides the entire page from assistive technology.', {}));
        } else {
          var focusables = Array.prototype.filter.call(el.querySelectorAll('*'), isTabbable);
          if (isTabbable(el)) focusables.unshift(el);
          if (focusables.length) out.push(fc('failed', el, 'aria-hidden-focusable', 'aria-hidden="true" subtree contains ' + focusables.length + ' element(s) in sequential focus order (e.g. ' + cssPath(focusables[0]) + '). Screen reader users land on content that is not announced.', { focusable: focusables.slice(0, 5).map(cssPath) }));
          else out.push(fc('passed', el, 'aria-hidden-focusable', 'aria-hidden="true" subtree has no focusable content.', {}));
        }
      }
    });
    return out;
  };

  rules['duplicate-id-aria'] = function () {
    var out = [];
    var referenced = {};
    document.querySelectorAll('[aria-labelledby], [aria-describedby], [aria-controls], [aria-owns], [aria-activedescendant], [aria-errormessage], [aria-details], [aria-flowto], label[for], [list], [headers], [form]').forEach(function (el) {
      IDREF_ATTRS.concat(['for', 'list', 'headers', 'form']).forEach(function (a) {
        var v = el.getAttribute(a);
        if (v) v.split(/\s+/).forEach(function (id) { if (id) referenced[id] = 1; });
      });
    });
    Object.keys(referenced).forEach(function (id) {
      var els = document.querySelectorAll('[id="' + esc(id) + '"]');
      if (els.length > 1) out.push(finding('failed', els[1], 'id="' + id + '" is used ' + els.length + ' times and is referenced by ARIA/label attributes; the association resolves to the first element only.', { id: id, count: els.length }));
    });
    return out;
  };

  rules['nested-interactive'] = function () {
    var out = [];
    var sel = 'a[href], button, input:not([type="hidden"]), select, textarea, summary, [role="button"], [role="link"], [role="checkbox"], [role="radio"], [role="switch"], [role="tab"], [role="menuitem"], [role="option"], [role="textbox"], [role="combobox"], [role="slider"]';
    document.querySelectorAll(sel).forEach(function (el) {
      if (!isVisible(el)) return;
      var inner = Array.prototype.filter.call(el.querySelectorAll(sel), isVisible);
      if (inner.length) out.push(finding('failed', el, '<' + el.nodeName.toLowerCase() + '> is interactive and contains ' + inner.length + ' other interactive element(s) (e.g. ' + cssPath(inner[0]) + '). Nested controls are announced and focused unpredictably.', { inner: inner.slice(0, 3).map(cssPath) }));
    });
    return out;
  };

  /* --- structure: landmarks, headings, lists, tables, language, title ---- */

  /* Landmarks are named only from author-provided labels, never from content. */
  function landmarkName(el) {
    var lb = el.getAttribute('aria-labelledby');
    if (lb) {
      var t = norm(lb.split(/\s+/).map(function (id) { var ref = document.getElementById(id); return ref ? textFromSubtree(ref, 0) : ''; }).join(' '));
      if (t) return t;
    }
    return norm(el.getAttribute('aria-label') || el.getAttribute('title') || '');
  }

  function landmarkRole(el) {
    var r = explicitRole(el);
    if (r && ['banner', 'complementary', 'contentinfo', 'form', 'main', 'navigation', 'region', 'search'].indexOf(r) >= 0) {
      if ((r === 'region' || r === 'form') && !landmarkName(el)) return null;
      return r;
    }
    if (r) return null;
    var cr = computedRole(el);
    if (cr === 'form' && !landmarkName(el)) return null;
    return ['banner', 'complementary', 'contentinfo', 'form', 'main', 'navigation', 'region'].indexOf(cr) >= 0 ? cr : null;
  }

  rules['landmarks'] = function () {
    var out = [];
    var landmarks = [];
    document.querySelectorAll('main, header, footer, nav, aside, section, form, [role]').forEach(function (el) {
      if (isHiddenFromAT(el)) return;
      var r = landmarkRole(el);
      if (r) landmarks.push({ el: el, role: r, name: lower(landmarkName(el)) });
    });
    var mains = landmarks.filter(function (l) { return l.role === 'main'; });
    if (mains.length === 0) out.push(finding('needsReview', null, 'No <main> landmark. Screen reader users cannot jump to the primary content.', {}));
    if (mains.length > 1) out.push(finding('failed', mains[1].el, mains.length + ' main landmarks; a page must expose exactly one.', { count: mains.length }));
    ['banner', 'contentinfo'].forEach(function (role) {
      var list = landmarks.filter(function (l) { return l.role === role; });
      if (list.length > 1) out.push(finding('failed', list[1].el, list.length + ' ' + role + ' landmarks; only one is allowed per page.', { count: list.length }));
      list.forEach(function (l) {
        var parentLandmark = landmarks.find(function (o) { return o.el !== l.el && o.el.contains(l.el); });
        if (parentLandmark) out.push(finding('failed', l.el, role + ' landmark is nested inside a ' + parentLandmark.role + ' landmark; it must be top level.', { parent: cssPath(parentLandmark.el) }));
      });
    });
    ['navigation', 'complementary', 'region', 'form', 'search'].forEach(function (role) {
      var list = landmarks.filter(function (l) { return l.role === role; });
      if (list.length < 2) return;
      var names = {};
      list.forEach(function (l) { names[l.name] = (names[l.name] || 0) + 1; });
      Object.keys(names).forEach(function (n) {
        if (names[n] > 1) {
          var sample = list.filter(function (l) { return l.name === n; })[1];
          out.push(finding('failed', sample.el, names[n] + ' ' + role + ' landmarks ' + (n ? 'share the name "' + n + '"' : 'have no name') + '; give each a unique aria-label so they can be distinguished in landmark lists.', { role: role, name: n, count: names[n] }));
        }
      });
    });
    // content outside landmarks
    if (landmarks.length) {
      var outside = [];
      Array.prototype.forEach.call(document.body.children, function (child) {
        walkOutside(child, outside, landmarks, 0);
      });
      if (outside.length) {
        var chars = outside.reduce(function (n, e) { return n + visibleText(e).length; }, 0);
        out.push(finding('needsReview', outside[0], outside.length + ' block(s) of content (' + chars + ' characters) sit outside any landmark, e.g. ' + cssPath(outside[0]) + '. Screen reader landmark navigation skips them.', { blocks: outside.slice(0, 5).map(cssPath), characters: chars }));
      }
    }
    if (out.length === 0) out.push(finding('passed', null, landmarks.length + ' landmark(s) with a single main, unique names and no nesting violations.', { landmarks: landmarks.map(function (l) { return l.role + (l.name ? '[' + l.name + ']' : ''); }) }));
    return out;
  };

  function walkOutside(el, acc, landmarks, depth) {
    if (depth > 6 || acc.length > 30 || !isVisible(el) || isHiddenFromAT(el)) return;
    if (el.nodeName === 'SCRIPT' || el.nodeName === 'STYLE') return;
    if (landmarks.some(function (l) { return l.el === el; })) return;
    if (landmarks.some(function (l) { return el.contains(l.el); })) {
      // mixed container: recurse
      Array.prototype.forEach.call(el.children, function (c) { walkOutside(c, acc, landmarks, depth + 1); });
      if (hasOwnText(el) && visibleText(el).length > 20) acc.push(el);
      return;
    }
    var t = visibleText(el);
    if (t.length >= 20 || el.querySelector('a[href], button, input, img')) acc.push(el);
  }

  rules['bypass-blocks'] = function () {
    var out = [];
    var tabs = tabbables();
    var skip = null;
    for (var i = 0; i < Math.min(5, tabs.length); i++) {
      var t = tabs[i];
      if (t.nodeName === 'A') {
        var href = t.getAttribute('href') || '';
        var hash = href.indexOf('#') >= 0 ? href.slice(href.indexOf('#') + 1) : '';
        if (hash && (document.getElementById(hash) || document.getElementsByName(hash).length) && /skip|jump|main|content/i.test(accessibleName(t) + ' ' + hash)) { skip = t; break; }
      }
    }
    var hasMain = !!document.querySelector('main, [role="main"]');
    var landmarkCount = document.querySelectorAll('main, nav, header, footer, aside, [role="main"], [role="navigation"], [role="banner"], [role="contentinfo"], [role="complementary"], [role="region"][aria-label], [role="region"][aria-labelledby], [role="search"]').length;
    var headings = document.querySelectorAll('h1, h2, h3, h4, h5, h6, [role="heading"]').length;
    var repeated = document.querySelectorAll('nav a[href], header a[href], [role="navigation"] a[href]').length;
    var data = { skipLink: skip ? cssPath(skip) : null, mainLandmark: hasMain, landmarks: landmarkCount, headings: headings, repeatedLinks: repeated };
    if (skip) out.push(finding('passed', skip, 'Skip link "' + norm(accessibleName(skip)) + '" is among the first focusable elements.', data));
    else if (hasMain || landmarkCount >= 2) out.push(finding('passed', null, 'No skip link, but landmarks allow bypassing repeated blocks (ARIA11).', data));
    else if (headings >= 2) out.push(finding('needsReview', null, 'No skip link or landmarks; only headings (' + headings + ') provide a bypass mechanism. Add a <main> landmark or a skip link.', data));
    else if (repeated >= 3) out.push(finding('failed', null, 'Repeated navigation (' + repeated + ' links) with no skip link, landmarks or heading structure to bypass it.', data));
    else out.push(finding('inapplicable', null, 'No repeated blocks of content detected.', data));
    return out;
  };

  rules['heading-structure'] = function () {
    var out = [];
    var hs = Array.prototype.filter.call(document.querySelectorAll('h1, h2, h3, h4, h5, h6, [role="heading"]'), function (h) { return isVisible(h) && !isHiddenFromAT(h); });
    var textLen = norm(document.body.innerText || '').length;
    if (hs.length === 0) {
      if (textLen > 400) out.push(finding('needsReview', null, 'Page has ' + textLen + ' characters of text but no headings; screen reader users cannot navigate by section.', { characters: textLen }));
      return out;
    }
    var prev = 0, h1s = 0;
    hs.forEach(function (h) {
      var lvl = parseInt(h.getAttribute('aria-level') || h.nodeName.replace('H', ''), 10);
      if (!(lvl >= 1)) lvl = 2;
      var name = norm(accessibleName(h));
      if (!name) out.push(fc('failed', h, 'heading-name', 'Empty heading (level ' + lvl + '); screen readers announce "heading level ' + lvl + '" with no text.', { level: lvl }));
      else out.push(fc('passed', h, 'heading-name', 'Heading has an accessible name.', { level: lvl, name: name.slice(0, 60) }));
      if (lvl === 1) h1s++;
      if (prev && lvl > prev + 1) out.push(finding('needsReview', h, 'Heading level jumps from h' + prev + ' to h' + lvl + ' ("' + name.slice(0, 60) + '"); skipped levels confuse the document outline.', { from: prev, to: lvl }));
      prev = lvl;
    });
    if (h1s === 0) out.push(finding('needsReview', hs[0], 'No level-1 heading; the page has no top-level title in the heading outline.', { headings: hs.length }));
    if (h1s > 1) out.push(finding('needsReview', null, h1s + ' level-1 headings; usually one h1 should name the page.', { h1: h1s }));
    if (out.length === 0) out.push(finding('passed', null, hs.length + ' headings form a consistent outline with a single h1.', { headings: hs.length }));
    return out;
  };

  rules['list-structure'] = function () {
    var out = [];
    document.querySelectorAll('ul, ol, [role="list"]').forEach(function (list) {
      if (explicitRole(list) && explicitRole(list) !== 'list') return;
      var bad = Array.prototype.filter.call(list.children, function (c) {
        if (c.nodeName === 'SCRIPT' || c.nodeName === 'TEMPLATE') return false;
        var r = explicitRole(c);
        if (r) return r !== 'listitem' && r !== 'presentation' && r !== 'none';
        return c.nodeName !== 'LI';
      });
      if (bad.length) out.push(finding('failed', list, 'List contains ' + bad.length + ' direct child(ren) that are not list items (e.g. <' + bad[0].nodeName.toLowerCase() + '>); screen readers report wrong item counts.', { badChildren: bad.slice(0, 3).map(cssPath) }));
    });
    document.querySelectorAll('li').forEach(function (li) {
      if (explicitRole(li)) return;
      var p = li.parentElement;
      if (p && (p.nodeName === 'UL' || p.nodeName === 'OL' || p.nodeName === 'MENU' || explicitRole(p) === 'list')) return;
      out.push(finding('failed', li, '<li> is not contained in a <ul>, <ol> or role="list" element.', {}));
    });
    document.querySelectorAll('dl').forEach(function (dl) {
      var bad = Array.prototype.filter.call(dl.children, function (c) { return ['DT', 'DD', 'DIV', 'SCRIPT', 'TEMPLATE'].indexOf(c.nodeName) < 0; });
      if (bad.length) out.push(finding('failed', dl, '<dl> contains elements other than <dt>/<dd>/<div> (e.g. <' + bad[0].nodeName.toLowerCase() + '>).', {}));
    });
    if (out.length === 0 && document.querySelector('ul, ol, dl, [role="list"]')) out.push(finding('passed', null, 'List markup is well-formed.', {}));
    return out;
  };

  rules['table-headers'] = function () {
    var out = [];
    document.querySelectorAll('table').forEach(function (table) {
      if (!isVisible(table) || isHiddenFromAT(table)) return;
      var r = explicitRole(table);
      if (r === 'presentation' || r === 'none') return;
      var rows = table.rows;
      if (rows.length < 2) return;
      var cols = 0;
      for (var i = 0; i < rows.length; i++) cols = Math.max(cols, rows[i].cells.length);
      if (cols < 2) return;
      var ths = table.querySelectorAll('th, [role="columnheader"], [role="rowheader"]');
      var data = { rows: rows.length, cols: cols, headers: ths.length };
      if (ths.length === 0) { out.push(fc('failed', table, 'no-headers', 'Data table (' + rows.length + 'x' + cols + ') has no header cells (<th> or role=columnheader/rowheader); screen readers cannot associate cells with headers.', data)); return; }
      var empty = Array.prototype.filter.call(ths, function (th) { return !norm(accessibleName(th)) && !norm(th.textContent); });
      if (empty.length) out.push(fc('needsReview', empty[0], 'no-headers', empty.length + ' header cell(s) are empty.', data));
      else out.push(fc('passed', table, 'no-headers', 'Data table has ' + ths.length + ' header cell(s).', data));
    });
    // d0f69e: each header cell must have assigned data cells
    document.querySelectorAll('table, [role="table"], [role="grid"], [role="treegrid"]').forEach(function (table) {
      if (!isVisible(table) || isHiddenFromAT(table)) return;
      var r = explicitRole(table);
      if (r && ['table', 'grid', 'treegrid'].indexOf(r) < 0) return; // the role overrides the table semantics
      var grid = table.nodeName === 'TABLE' ? tableGrid(table) : ariaGrid(table);
      if (!grid) return;
      grid.headers.forEach(function (h) {
        if (h.assigned > 0) out.push(fc('passed', h.el, 'header-assigned', 'Header cell "' + norm(h.el.textContent).slice(0, 40) + '" has ' + h.assigned + ' assigned cell(s).', { assigned: h.assigned }));
        else out.push(fc('failed', h.el, 'header-assigned', 'Header cell "' + norm(h.el.textContent).slice(0, 40) + '" has no data cells assigned to it; it announces a relationship that does not exist.', { assigned: 0 }));
      });
    });
    // a25f45: headers attribute must refer to cells in the same table
    document.querySelectorAll('td[headers], th[headers]').forEach(function (cell) {
      var table = cell.closest('table');
      if (!table || !isVisible(table)) return;
      var tr = explicitRole(table);
      if (tr && ['table', 'grid', 'treegrid'].indexOf(tr) < 0) return;
      var ids = norm(cell.getAttribute('headers')).split(/\s+/).filter(Boolean);
      if (!ids.length) return;
      var bad = ids.filter(function (id) {
        var ref = document.getElementById(id);
        return !ref || ref.closest('table') !== table || !(ref.nodeName === 'TH' || ref.nodeName === 'TD') || ref === cell;
      });
      if (bad.length) out.push(fc('failed', cell, 'headers-same-table', 'headers="' + cell.getAttribute('headers') + '" refers to ' + bad.join(', ') + ', which is not a cell in the same table (or is the cell itself).', { bad: bad }));
      else out.push(fc('passed', cell, 'headers-same-table', 'headers attribute refers to cells in the same table.', { headers: ids }));
    });
    return out;
  };

  /* Builds a cell grid honouring colspan/rowspan and returns header cells with the number of data cells assigned to each. */
  function tableGrid(table) {
    var rows = table.rows;
    if (!rows.length) return null;
    var grid = [];
    for (var r = 0; r < rows.length; r++) {
      var col = 0;
      for (var c = 0; c < rows[r].cells.length; c++) {
        var cell = rows[r].cells[c];
        while (grid[r] && grid[r][col]) col++;
        var cs = cell.colSpan || 1, rs = cell.rowSpan || 1;
        for (var dr = 0; dr < rs; dr++) {
          for (var dc = 0; dc < cs; dc++) {
            grid[r + dr] = grid[r + dr] || [];
            grid[r + dr][col + dc] = cell;
          }
        }
        col += cs;
      }
    }
    var headers = [];
    var explicit = {};
    table.querySelectorAll('[headers]').forEach(function (cell) {
      norm(cell.getAttribute('headers')).split(/\s+/).forEach(function (id) { if (id) explicit[id] = (explicit[id] || 0) + 1; });
    });
    for (var rr = 0; rr < grid.length; rr++) {
      if (!grid[rr]) continue;
      for (var cc = 0; cc < grid[rr].length; cc++) {
        var el = grid[rr][cc];
        if (!el || el.__seen === table) continue;
        var role = explicitRole(el);
        var isHeader = el.nodeName === 'TH' || role === 'columnheader' || role === 'rowheader';
        if (!isHeader) continue;
        el.__seen = table;
        var assigned = el.id && explicit[el.id] ? explicit[el.id] : 0;
        var scope = lower(el.getAttribute('scope'));
        var dir = scope === 'row' ? 'row' : scope === 'col' ? 'col' : null;
        if (!dir) {
          // infer: a header in the first column of its row acts as a row header, otherwise a column header
          dir = cc === 0 && grid[rr].length > 1 && !isHeaderAt(grid, rr, 1) ? 'row' : 'col';
        }
        if (dir === 'col') {
          for (var r2 = rr + 1; r2 < grid.length; r2++) {
            var below = grid[r2] && grid[r2][cc];
            if (below && below !== el && !below.hasAttribute('headers')) assigned++;
          }
        } else {
          for (var c2 = cc + 1; grid[rr] && c2 < grid[rr].length; c2++) {
            var right = grid[rr][c2];
            if (right && right !== el && !right.hasAttribute('headers')) assigned++;
          }
        }
        headers.push({ el: el, assigned: assigned, direction: dir });
      }
    }
    return { grid: grid, headers: headers };
  }

  /* Grid built from ARIA row/cell roles rather than table markup. */
  function ariaGrid(container) {
    var rows = Array.prototype.filter.call(container.querySelectorAll('[role="row"]'), function (r) { return r.closest('[role="table"], [role="grid"], [role="treegrid"]') === container; });
    if (!rows.length) return null;
    var grid = [];
    rows.forEach(function (row, ri) {
      var cells = Array.prototype.filter.call(row.querySelectorAll('[role="cell"], [role="gridcell"], [role="columnheader"], [role="rowheader"]'), function (c) { return c.closest('[role="row"]') === row; });
      grid[ri] = cells;
    });
    var headers = [];
    grid.forEach(function (cells, ri) {
      cells.forEach(function (el, ci) {
        var role = explicitRole(el);
        if (role !== 'columnheader' && role !== 'rowheader') return;
        var assigned = 0;
        if (role === 'columnheader') {
          for (var r2 = ri + 1; r2 < grid.length; r2++) if (grid[r2] && grid[r2][ci]) assigned++;
        } else {
          for (var c2 = ci + 1; c2 < cells.length; c2++) assigned++;
        }
        headers.push({ el: el, assigned: assigned, direction: role === 'columnheader' ? 'col' : 'row' });
      });
    });
    return { grid: grid, headers: headers };
  }

  function isHeaderAt(grid, r, c) {
    var el = grid[r] && grid[r][c];
    if (!el) return false;
    var role = explicitRole(el);
    return el.nodeName === 'TH' || role === 'columnheader' || role === 'rowheader';
  }

  /* 24afc2 / 78fd32 / 9e45ec: !important spacing declarations in style attributes must not block the
     WCAG 1.4.12 values. */
  var SPACING_CHECKS = [
    { prop: 'letter-spacing', check: 'letter-spacing', factor: 0.12, label: '0.12em' },
    { prop: 'word-spacing', check: 'word-spacing', factor: 0.16, label: '0.16em' },
    { prop: 'line-height', check: 'line-height', factor: 1.5, label: '1.5' }
  ];

  var CSS_WIDE_KEYWORDS = ['inherit', 'initial', 'unset', 'revert', 'revert-layer'];

  /** Number of line boxes the element's own text occupies. */
  function lineBoxCount(el) {
    var count = 0;
    for (var i = 0; i < el.childNodes.length; i++) {
      var n = el.childNodes[i];
      if (n.nodeType !== 3 || !norm(n.nodeValue)) continue;
      var range = document.createRange();
      range.selectNodeContents(n);
      var tops = {};
      var rects = range.getClientRects();
      for (var j = 0; j < rects.length; j++) tops[Math.round(rects[j].top)] = 1;
      count += Object.keys(tops).length;
    }
    return count;
  }

  rules['text-spacing-style-attr'] = function () {
    var out = [];
    document.querySelectorAll('[style]').forEach(function (el) {
      var decl = el.getAttribute('style') || '';
      SPACING_CHECKS.forEach(function (spec) {
        var re = new RegExp('(?:^|;)\\s*' + spec.prop + '\\s*:\\s*([^;!]+?)\\s*!\\s*important', 'i');
        var m = decl.match(re);
        if (!m) return;
        var raw = norm(m[1]);
        // a CSS-wide keyword does not fix a value, so a user stylesheet can still take effect
        if (CSS_WIDE_KEYWORDS.indexOf(lower(raw)) >= 0) return;
        if (!hasOwnText(el) || !isOnScreen(el)) return;
        // line-height only affects text that occupies more than one line
        if (spec.prop === 'line-height' && lineBoxCount(el) < 2) return;
        var cs = style(el);
        if (!cs) return;
        var fontSize = parseFloat(cs.fontSize);
        var actual = parseFloat(cs[spec.prop === 'line-height' ? 'lineHeight' : spec.prop === 'letter-spacing' ? 'letterSpacing' : 'wordSpacing']);
        if (spec.prop !== 'line-height' && lower(raw) === 'normal') actual = 0;
        var required = fontSize * spec.factor;
        var data = { property: spec.prop, declared: raw, computedPx: isNaN(actual) ? null : Math.round(actual * 100) / 100, requiredPx: Math.round(required * 100) / 100, fontSizePx: fontSize };
        if (isNaN(actual)) { out.push(fc('cantTell', el, spec.check, spec.prop + ' is set with !important but the computed value could not be resolved.', data)); return; }
        if (actual + 0.05 >= required) out.push(fc('passed', el, spec.check, spec.prop + ' is fixed with !important at ' + data.computedPx + 'px, which still meets the WCAG ' + spec.label + ' requirement.', data));
        else out.push(fc('failed', el, spec.check, spec.prop + ' is fixed with !important at ' + data.computedPx + 'px, below the ' + spec.label + ' (' + data.requiredPx + 'px) users must be able to apply. The declaration cannot be overridden by a user stylesheet.', data));
      });
    });
    return out;
  };

  rules['html-lang'] = function () {
    var el = document.documentElement;
    var lang = el.getAttribute('lang');
    var xml = el.getAttribute('xml:lang');
    var out = [];
    if (lang === null) {
      if (xml === null) out.push(fc('failed', el, 'lang-present', 'The <html> element has no lang attribute; screen readers cannot pick the right pronunciation rules.', { lang: null }));
      else out.push(fc('failed', el, 'lang-present', 'The <html> element only has xml:lang; HTML documents need a lang attribute.', { xmlLang: xml }));
      return out;
    }
    if (!norm(lang)) {
      out.push(fc('failed', el, 'lang-present', 'The <html> lang attribute is empty.', { lang: lang }));
      return out;
    }
    out.push(fc('passed', el, 'lang-present', 'The <html> element declares a language.', { lang: lang }));
    var validity = langValidity(lang);
    if (validity === 'invalid') out.push(fc('failed', el, 'lang-valid', 'lang="' + lang + '" does not start with a valid primary language subtag from the IANA registry.', { lang: lang }));
    else if (validity === 'unknown') out.push(fc('cantTell', el, 'lang-valid', 'lang="' + lang + '" uses a three-letter primary subtag; confirm it is in the IANA language subtag registry.', { lang: lang }));
    else out.push(fc('passed', el, 'lang-valid', 'Page language "' + lang + '" is a valid language tag.', { lang: lang }));
    if (xml && norm(xml) && lower(xml.split('-')[0]) !== lower(lang.split('-')[0])) {
      out.push(fc('failed', el, 'lang-valid', 'lang="' + lang + '" and xml:lang="' + xml + '" declare different languages.', { lang: lang, xmlLang: xml }));
    }
    return out;
  };

  rules['lang-attr-valid'] = function () {
    var out = [];
    document.querySelectorAll('body [lang], body [xml\\:lang]').forEach(function (el) {
      var v = el.getAttribute('lang');
      if (v === null || v === '') return; // no declaration to validate
      if (!hasPerceivableContent(el, true, 0)) return; // a nested lang declaration governs all the text here
      var validity = langValidity(v);
      if (validity === 'invalid') out.push(fc('failed', el, 'part-lang-valid', 'lang="' + v + '" does not start with a valid primary language subtag from the IANA registry.', { lang: v }));
      else if (validity === 'unknown') out.push(fc('cantTell', el, 'part-lang-valid', 'lang="' + v + '" uses a three-letter primary subtag; confirm it is in the IANA language subtag registry.', { lang: v }));
      else out.push(fc('passed', el, 'part-lang-valid', 'Language of part: "' + v + '".', { lang: v }));
    });
    return out;
  };

  rules['document-title'] = function () {
    var titles = document.querySelectorAll('head title, title');
    var t = titles.length ? norm(titles[0].textContent) : '';
    if (!titles.length) return [fc('failed', null, 'title-present', 'The page has no <title>; it is the first thing a screen reader announces and it identifies the tab/window.', {})];
    if (!t) return [fc('failed', null, 'title-present', 'The <title> element is empty.', {})];
    var out = [fc('passed', null, 'title-present', 'Page title: "' + t + '".', { title: t })];
    if (/^(untitled|home|index|document|new page|page|welcome|title)$/i.test(t) || /\.(html?|php|aspx?)$/i.test(t)) {
      out.push(fc('needsReview', null, 'title-descriptive', 'Page title "' + t + '" is generic and does not describe the page topic or purpose.', { title: t }));
    } else {
      out.push(fc('passed', null, 'title-descriptive', 'Page title "' + t + '" appears to describe the page.', { title: t }));
    }
    return out;
  };

  rules['meta-viewport-zoom'] = function () {
    var out = [];
    document.querySelectorAll('meta[name="viewport" i]').forEach(function (m) {
      var content = m.getAttribute('content');
      if (content === null || !norm(content)) return;
      var props = {};
      norm(content).toLowerCase().split(',').forEach(function (pair) {
        var kv = pair.split('=');
        if (kv.length === 2) props[norm(kv[0])] = norm(kv[1]);
      });
      var data = { content: content, userScalable: props['user-scalable'], maximumScale: props['maximum-scale'] };
      if (!('user-scalable' in props) && !('maximum-scale' in props)) return; // nothing that could restrict zoom
      var reasons = [];
      if ('user-scalable' in props) {
        var us = props['user-scalable'];
        if (us === 'device-width' || us === 'device-height') { /* resolves to a large number: zoom allowed */ }
        else if (us === 'yes') { /* allowed */ }
        else {
          var n = parseFloat(us);
          // per CSS Device Adaptation a value is truncated towards zero, and 0 or an unparseable value means "no"
          if (isNaN(n) || Math.trunc(n) === 0) reasons.push('user-scalable=' + us + ' prevents the user from zooming');
        }
      }
      if ('maximum-scale' in props) {
        var ms = props['maximum-scale'];
        if (ms === 'device-width' || ms === 'device-height') { /* large: zoom allowed */ }
        else {
          var mn = parseFloat(ms);
          if (isNaN(mn)) reasons.push('maximum-scale=' + ms + ' is not a number, so it clamps scaling');
          else if (mn >= 0 && mn < 2) reasons.push('maximum-scale=' + ms + ' caps zoom below 200%');
        }
      }
      if (reasons.length) out.push(fc('failed', m, 'viewport-zoom', 'Viewport meta restricts zooming: ' + reasons.join('; ') + ' (content="' + content + '").', data));
      else out.push(fc('passed', m, 'viewport-zoom', 'Viewport meta allows zooming to at least 200%.', data));
    });
    return out;
  };

  rules['scrollable-region-focusable'] = function () {
    var out = [];
    document.querySelectorAll('body *').forEach(function (el) {
      if (!isVisible(el)) return;
      var cs = style(el);
      if (!cs) return;
      var scrollsY = (cs.overflowY === 'auto' || cs.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 4;
      var scrollsX = (cs.overflowX === 'auto' || cs.overflowX === 'scroll') && el.scrollWidth > el.clientWidth + 4;
      if (!scrollsY && !scrollsX) return;
      if (el.clientHeight === 0 || el.clientWidth === 0) return;
      // an empty spacer that merely produces a scrollbar has nothing for anyone to reach
      if (!hasPerceivableContent(el, false, 0)) return;
      if (isFocusable(el)) { out.push(finding('passed', el, 'Scrollable region is keyboard focusable.', {})); return; }
      var inner = Array.prototype.filter.call(el.querySelectorAll('a[href], button, input, select, textarea, [tabindex]'), isTabbable);
      if (inner.length) { out.push(finding('passed', el, 'Scrollable region contains focusable content.', {})); return; }
      out.push(finding('failed', el, 'Scrollable region (' + (scrollsY ? 'vertical' : 'horizontal') + ' overflow) is not keyboard focusable and has no focusable content; keyboard users cannot scroll it. Add tabindex="0".', { scrollHeight: el.scrollHeight, clientHeight: el.clientHeight }));
    });
    return out;
  };

  rules['live-regions'] = function () {
    var out = [];
    document.querySelectorAll('[aria-live], [role="alert"], [role="status"], [role="log"], [role="marquee"], [role="timer"]').forEach(function (el) {
      var live = lower(el.getAttribute('aria-live')) || (explicitRole(el) === 'alert' ? 'assertive' : 'polite');
      var text = norm(el.textContent);
      var descendants = el.querySelectorAll('*').length;
      var data = { live: live, role: explicitRole(el), textLength: text.length, descendants: descendants };
      if (live === 'assertive' && text.length > 0) out.push(finding('needsReview', el, 'Assertive live region already contains ' + text.length + ' characters on load; assertive interrupts the user and should be reserved for urgent status messages.', data));
      else if (descendants > 40) out.push(finding('needsReview', el, 'Live region wraps a large subtree (' + descendants + ' elements); any DOM change inside will be announced. Scope live regions to the status message itself.', data));
      else if (live !== 'off' && el.querySelector('a[href], button, input, select, textarea')) out.push(finding('needsReview', el, 'Live region contains interactive controls; changes to them will be announced unexpectedly.', data));
      else out.push(finding('passed', el, 'Live region (' + live + ') is scoped.', data));
    });
    return out;
  };

  /* HTML autofill field names, split as the spec does: contact fields may be preceded by a contact modifier. */
  var AUTOFILL_FIELDS = ['name', 'honorific-prefix', 'given-name', 'additional-name', 'family-name', 'honorific-suffix', 'nickname', 'username', 'new-password', 'current-password', 'one-time-code', 'organization-title', 'organization', 'street-address', 'address-line1', 'address-line2', 'address-line3', 'address-level4', 'address-level3', 'address-level2', 'address-level1', 'country', 'country-name', 'postal-code', 'cc-name', 'cc-given-name', 'cc-additional-name', 'cc-family-name', 'cc-number', 'cc-exp', 'cc-exp-month', 'cc-exp-year', 'cc-csc', 'cc-type', 'transaction-currency', 'transaction-amount', 'language', 'bday', 'bday-day', 'bday-month', 'bday-year', 'sex', 'url', 'photo'];
  var AUTOFILL_CONTACT_FIELDS = ['tel', 'tel-country-code', 'tel-national', 'tel-area-code', 'tel-local', 'tel-local-prefix', 'tel-local-suffix', 'tel-extension', 'email', 'impp'];
  var AUTOFILL_MODES = ['shipping', 'billing'];
  var AUTOFILL_CONTACT_MODIFIERS = ['home', 'work', 'mobile', 'fax', 'pager'];

  /*
   * Validates a value against the HTML autofill detail-tokens grammar:
   *   [section-*] [shipping|billing] [[home|work|mobile|fax|pager] contact-field | field] [webauthn]
   * Order matters, exactly one field name is allowed, and a contact modifier requires a contact field.
   */
  function parseAutocomplete(value) {
    var tokens = norm(value).toLowerCase().split(/\s+/).filter(Boolean);
    if (!tokens.length) return { valid: false, reason: 'the value is empty' };
    if (tokens.length === 1 && (tokens[0] === 'on' || tokens[0] === 'off')) return { valid: true, keyword: tokens[0] };
    if (tokens.indexOf('on') >= 0 || tokens.indexOf('off') >= 0) return { valid: false, reason: '"on"/"off" cannot be combined with other tokens' };
    var i = 0;
    if (/^section-/.test(tokens[i])) i++;
    if (AUTOFILL_MODES.indexOf(tokens[i]) >= 0) i++;
    var modifier = null;
    if (AUTOFILL_CONTACT_MODIFIERS.indexOf(tokens[i]) >= 0) { modifier = tokens[i]; i++; }
    var field = tokens[i];
    if (field === undefined) return { valid: false, reason: 'no autofill field name is present' };
    var isContact = AUTOFILL_CONTACT_FIELDS.indexOf(field) >= 0;
    var isPlain = AUTOFILL_FIELDS.indexOf(field) >= 0;
    if (!isContact && !isPlain) {
      var known = AUTOFILL_MODES.concat(AUTOFILL_CONTACT_MODIFIERS).indexOf(field) >= 0;
      return { valid: false, reason: known ? '"' + field + '" is a modifier, not a field name, and appears out of order' : '"' + field + '" is not an autofill field name' };
    }
    if (modifier && !isContact) return { valid: false, reason: '"' + modifier + '" may only precede a telephone or email field, not "' + field + '"' };
    i++;
    if (tokens[i] === 'webauthn') i++;
    if (i < tokens.length) return { valid: false, reason: 'unexpected token "' + tokens[i] + '" after the field name' };
    return { valid: true, field: field };
  }

  rules['autocomplete-valid'] = function () {
    var out = [];
    document.querySelectorAll('input[autocomplete], select[autocomplete], textarea[autocomplete]').forEach(function (el) {
      var raw = el.getAttribute('autocomplete');
      if (raw === null || norm(raw) === '') return; // nothing declared, so nothing to validate
      var t = lower(el.type);
      if (el.nodeName === 'INPUT' && ['hidden', 'button', 'submit', 'reset', 'image', 'checkbox', 'radio', 'file', 'color', 'range'].indexOf(t) >= 0) return;
      // an inactive or hidden control is not conveying a purpose to anyone
      if (el.disabled || lower(el.getAttribute('aria-disabled')) === 'true' || el.closest('[aria-disabled="true"]')) return;
      if (!inAccessibilityTree(el)) return;
      var parsed = parseAutocomplete(raw);
      var data = { autocomplete: raw, field: parsed.field || null };
      if (parsed.keyword) return; // "on"/"off" express no purpose, so there is nothing to validate
      if (parsed.valid) out.push(fc('passed', el, 'autocomplete-valid', 'autocomplete="' + raw + '" is a valid autofill field name.', data));
      else out.push(fc('failed', el, 'autocomplete-valid', 'autocomplete="' + raw + '" is not valid: ' + parsed.reason + '. Browsers and assistive technology cannot identify the field purpose.', data));
    });
    return out;
  };

  /* --------------------------------------------------------- runtime helpers */

  var FOCUS_PROPS = ['outlineStyle', 'outlineWidth', 'outlineColor', 'outlineOffset', 'boxShadow', 'borderTopColor', 'borderTopWidth', 'borderTopStyle', 'borderBottomColor', 'backgroundColor', 'color', 'textDecorationLine', 'filter', 'transform', 'opacity'];

  function styleSummary(el) {
    var res = {};
    var cs = style(el);
    if (!cs) return res;
    FOCUS_PROPS.forEach(function (p) { res[p] = cs[p]; });
    var before = style(el, '::before'), after = style(el, '::after');
    ['outlineStyle', 'outlineWidth', 'boxShadow', 'borderTopWidth', 'borderTopStyle', 'backgroundColor', 'opacity', 'content', 'display'].forEach(function (p) {
      res['before.' + p] = before ? before[p] : null;
      res['after.' + p] = after ? after[p] : null;
    });
    if (el.parentElement) {
      var pcs = style(el.parentElement);
      ['outlineStyle', 'outlineWidth', 'boxShadow', 'borderTopColor', 'backgroundColor'].forEach(function (p) { res['parent.' + p] = pcs ? pcs[p] : null; });
    }
    return res;
  }

  A.tabbables = function () {
    return tabbables().map(function (el) {
      return { selector: cssPath(el), tag: el.nodeName.toLowerCase(), name: accessibleName(el).slice(0, 80), rect: rect(el), tabindex: el.getAttribute('tabindex'), styles: styleSummary(el) };
    });
  };

  A.blur = function () {
    if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur();
    window.scrollTo(0, 0);
    return true;
  };

  A.activeElement = function () {
    var el = document.activeElement;
    if (!el || el === document.body || el === document.documentElement) return { body: true };
    var r = el.getBoundingClientRect();
    return {
      body: false,
      selector: cssPath(el),
      tag: el.nodeName.toLowerCase(),
      name: accessibleName(el).slice(0, 80),
      html: snippet(el),
      rect: rect(el),
      tabindex: el.getAttribute('tabindex'),
      styles: styleSummary(el),
      inViewport: r.bottom > 0 && r.right > 0 && r.top < innerHeight && r.left < innerWidth,
      viewport: { width: innerWidth, height: innerHeight }
    };
  };

  /* Which sample points of the focused element are covered by other content (sticky headers, banners, dialogs). */
  A.obscured = function () {
    var el = document.activeElement;
    if (!el || el === document.body) return null;
    var r = el.getBoundingClientRect();
    if (r.width === 0 || r.height === 0) return null;
    var inset = Math.min(2, r.width / 4, r.height / 4);
    var pts = [
      [r.left + r.width / 2, r.top + r.height / 2],
      [r.left + inset, r.top + inset], [r.right - inset, r.top + inset],
      [r.left + inset, r.bottom - inset], [r.right - inset, r.bottom - inset]
    ];
    var covered = 0, total = 0, by = null;
    pts.forEach(function (p) {
      if (p[0] < 0 || p[1] < 0 || p[0] >= innerWidth || p[1] >= innerHeight) return; // off-screen points don't count as obscured by content
      total++;
      var top = document.elementFromPoint(p[0], p[1]);
      if (!top) return;
      if (top === el || el.contains(top) || top.contains(el)) return;
      var tcs = style(top);
      var bg = parseColor(tcs ? tcs.backgroundColor : '');
      var paints = (bg && bg.a > 0) || (tcs && tcs.backgroundImage !== 'none') || norm(top.textContent).length > 0 || top.nodeName === 'IMG' || top.nodeName === 'IFRAME';
      if (!paints) return;
      covered++;
      if (!by) by = { selector: cssPath(top), html: snippet(top), position: tcs ? tcs.position : null };
    });
    return { sampled: total, covered: covered, by: by, fully: total > 0 && covered === total, partially: covered > 0 && covered < total };
  };

  A.installStyle = function (arg) {
    var old = document.getElementById(arg.id);
    if (old) old.remove();
    var s = document.createElement('style');
    s.id = arg.id;
    s.textContent = arg.css;
    document.head.appendChild(s);
    return true;
  };

  A.removeStyle = function (id) {
    var s = document.getElementById(id);
    if (s) s.remove();
    return true;
  };

  A.horizontalOverflow = function () {
    var se = document.scrollingElement || document.documentElement;
    var vw = document.documentElement.clientWidth;
    var offenders = [];
    var all = document.querySelectorAll('body *');
    for (var i = 0; i < all.length && offenders.length < 10; i++) {
      var el = all[i];
      if (!isVisible(el)) continue;
      var r = el.getBoundingClientRect();
      if (r.right > vw + 2 && r.width > 0) {
        var cs = style(el);
        if (cs && cs.position === 'fixed') continue;
        offenders.push({ selector: cssPath(el), html: snippet(el), right: Math.round(r.right), width: Math.round(r.width) });
      }
    }
    return { scrollWidth: se.scrollWidth, clientWidth: vw, overflow: se.scrollWidth > vw + 1, offenders: offenders };
  };

  /* Text nodes clipped by their container or overlapping a sibling. Used before/after injecting text-spacing CSS. */
  A.textClipping = function () {
    var issues = [];
    var all = document.querySelectorAll('body *');
    for (var i = 0; i < all.length && issues.length < 40; i++) {
      var el = all[i];
      if (!hasOwnText(el) || !isVisible(el)) continue;
      var cs = style(el);
      if (!cs) continue;
      var clipsX = (cs.overflowX === 'hidden' || cs.overflowX === 'clip') && el.scrollWidth > el.clientWidth + 2;
      var clipsY = (cs.overflowY === 'hidden' || cs.overflowY === 'clip') && el.scrollHeight > el.clientHeight + 2;
      var ellipsis = cs.textOverflow === 'ellipsis' && el.scrollWidth > el.clientWidth + 2;
      if (clipsX || clipsY || ellipsis) {
        issues.push({ selector: cssPath(el), html: snippet(el), reason: ellipsis ? 'truncated with ellipsis' : (clipsY ? 'vertically clipped' : 'horizontally clipped'), scrollWidth: el.scrollWidth, clientWidth: el.clientWidth, scrollHeight: el.scrollHeight, clientHeight: el.clientHeight });
        continue;
      }
      // overlap with next visible sibling that also has text
      var sib = el.nextElementSibling;
      if (sib && isVisible(sib) && hasOwnText(sib) && cs.position !== 'absolute' && cs.position !== 'fixed') {
        var a = el.getBoundingClientRect(), b = sib.getBoundingClientRect();
        var overlapX = Math.min(a.right, b.right) - Math.max(a.left, b.left);
        var overlapY = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
        if (overlapX > 4 && overlapY > 4) {
          issues.push({ selector: cssPath(el), html: snippet(el), reason: 'overlaps following sibling ' + cssPath(sib), overlapX: Math.round(overlapX), overlapY: Math.round(overlapY) });
        }
      }
    }
    return issues;
  };

  /* --------------------------------------------------------------- snapshot */

  var HELP_RE = /\b(help|support|contact( us)?|faq|frequently asked|customer (service|care)|live chat|chat with|assistance|get in touch)\b/i;

  function region(el) {
    if (el.closest('header, [role="banner"]')) return 'header';
    if (el.closest('footer, [role="contentinfo"]')) return 'footer';
    if (el.closest('nav, [role="navigation"]')) return 'nav';
    if (el.closest('main, [role="main"]')) return 'main';
    return 'other';
  }

  A.snapshot = function () {
    var links = linkTargets();
    var order = 0;
    var linkInfo = links.map(function (a) {
      return { name: norm(accessibleName(a)), href: normHref(a), region: region(a), order: order++, selector: cssPath(a) };
    });
    var navs = [];
    document.querySelectorAll('nav, [role="navigation"], header, footer, [role="banner"], [role="contentinfo"]').forEach(function (n) {
      if (!isVisible(n)) return;
      var isNav = n.matches('nav, [role="navigation"]');
      var items = Array.prototype.filter.call(n.querySelectorAll('a[href], button'), function (a) {
        if (!isVisible(a)) return false;
        // header/footer regions only own the controls that are not already inside a nested nav region
        return isNav || !a.closest('nav, [role="navigation"]');
      }).map(function (a) { return norm(accessibleName(a)); }).filter(Boolean);
      if (items.length === 0) return;
      navs.push({ selector: cssPath(n), label: norm(n.getAttribute('aria-label') || ''), tag: n.nodeName.toLowerCase(), items: items });
    });
    var help = linkInfo.filter(function (l) { return HELP_RE.test(l.name) || HELP_RE.test(l.href); });
    document.querySelectorAll('button').forEach(function (b) {
      if (!isVisible(b)) return;
      var n = norm(accessibleName(b));
      if (HELP_RE.test(n)) help.push({ name: n, href: '', region: region(b), order: -1, selector: cssPath(b) });
    });
    var icons = [];
    document.querySelectorAll('a[href] img, button img, a[href] svg, button svg, [role="button"] img').forEach(function (img) {
      var ctl = img.closest('a[href], button, [role="button"]');
      if (!ctl || !isVisible(ctl)) return;
      var src = img.nodeName === 'IMG' ? (img.getAttribute('src') || '') : ('svg:' + (img.getAttribute('data-icon') || img.getAttribute('class') || (img.querySelector('use') ? img.querySelector('use').getAttribute('href') || img.querySelector('use').getAttribute('xlink:href') : '') || ''));
      icons.push({ src: src.split('?')[0], name: norm(accessibleName(ctl)), selector: cssPath(ctl) });
    });
    var fields = [];
    document.querySelectorAll('input:not([type="hidden"]):not([type="submit"]):not([type="button"]):not([type="reset"]):not([type="checkbox"]):not([type="radio"]):not([type="search"]), select, textarea').forEach(function (el) {
      if (!isVisible(el)) return;
      fields.push({
        selector: cssPath(el),
        label: lower(accessibleName(el)),
        name: lower(el.getAttribute('name') || ''),
        type: lower(el.type || el.nodeName),
        autocomplete: lower(el.getAttribute('autocomplete') || ''),
        prefilled: !!(el.value && norm(el.value)),
        required: el.required
      });
    });
    var hasSearch = !!document.querySelector('input[type="search"], [role="search"], form[action*="search" i], input[name*="search" i], input[placeholder*="search" i], input[aria-label*="search" i]');
    var hasSitemap = linkInfo.some(function (l) { return /site ?map/i.test(l.name) || /sitemap/i.test(l.href); });
    var hasBreadcrumb = !!document.querySelector('nav[aria-label*="breadcrumb" i], [class*="breadcrumb" i], [itemtype*="BreadcrumbList"]');
    var hasToc = !!document.querySelector('nav[aria-label*="contents" i], [class*="toc" i], [id*="toc" i]');
    return {
      url: location.href,
      title: document.title,
      links: linkInfo,
      navs: navs,
      help: help,
      icons: icons,
      fields: fields,
      hasSearch: hasSearch,
      hasSitemap: hasSitemap,
      hasBreadcrumb: hasBreadcrumb,
      hasToc: hasToc,
      navLinkCount: navs.reduce(function (n, x) { return n + x.items.length; }, 0)
    };
  };

  /* ---------------------------------------------------------------- export */

  A.rules = Object.keys(rules);

  A.runRule = function (id, options) {
    var fn = rules[id];
    if (!fn) throw new Error('Unknown in-page rule: ' + id);
    var res = fn(options || {});
    return res.length ? res : [finding('inapplicable', null, 'No applicable targets on this page.', {})];
  };

  A.accessibleName = function (selector) {
    var el = document.querySelector(selector);
    return el ? accessibleName(el) : null;
  };

  A.elementCrop = function (arg) {
    var el = document.querySelector(arg.selector);
    if (!el) return null;
    el.scrollIntoView({ block: 'center', inline: 'center' });
    var r = el.getBoundingClientRect();
    var pad = arg.pad || 0;
    var x = Math.max(0, r.left - pad), y = Math.max(0, r.top - pad);
    var right = Math.min(innerWidth, r.right + pad), bottom = Math.min(innerHeight, r.bottom + pad);
    return { x: x, y: y, width: Math.max(1, right - x), height: Math.max(1, bottom - y) };
  };

  window.__a11yAgent = A;
})();
