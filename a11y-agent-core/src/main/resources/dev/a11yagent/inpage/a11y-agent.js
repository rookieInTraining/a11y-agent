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

  rules['label-in-name'] = function () {
    var out = [];
    var sel = 'a[href], button, input[type="button"], input[type="submit"], input[type="reset"], summary, [role="button"], [role="link"], [role="tab"], [role="menuitem"], [role="checkbox"], [role="radio"], [role="switch"], input, select, textarea';
    document.querySelectorAll(sel).forEach(function (el) {
      if (!isVisible(el) || isHiddenFromAT(el)) return;
      if (!el.hasAttribute('aria-label') && !el.hasAttribute('aria-labelledby')) return;
      var name = lower(accessibleName(el));
      var visible;
      if (el.nodeName === 'INPUT' || el.nodeName === 'SELECT' || el.nodeName === 'TEXTAREA') {
        visible = lower(labelText(el));
        if (!visible && el.nodeName === 'INPUT' && (el.type === 'button' || el.type === 'submit' || el.type === 'reset')) visible = lower(el.value);
      } else {
        visible = lower(visibleText(el));
      }
      visible = visible.replace(/[^\p{L}\p{N} ]/gu, '').trim();
      var cmpName = name.replace(/[^\p{L}\p{N} ]/gu, '').trim();
      if (!visible || !cmpName) return;
      var data = { accessibleName: name, visibleLabel: visible };
      if (cmpName.indexOf(visible) >= 0) {
        out.push(finding('passed', el, 'Accessible name contains the visible label.', data));
      } else {
        out.push(finding('failed', el, 'Accessible name "' + name + '" does not contain the visible label "' + visible + '". Speech-input users cannot activate it by saying what they see.', data));
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
    document.querySelectorAll('meta[http-equiv]').forEach(function (m) {
      if (lower(m.getAttribute('http-equiv')) !== 'refresh') return;
      var content = m.getAttribute('content') || '';
      var secs = parseInt(content, 10);
      var data = { content: content, seconds: secs };
      if (isNaN(secs)) return;
      if (secs === 0) {
        out.push(finding('passed', m, 'Immediate client-side redirect (delay 0) is exempt.', data));
      } else if (secs < 72000) {
        out.push(finding('failed', m, 'Page refreshes or redirects automatically after ' + secs + 's with no way to turn off, adjust or extend the time limit (F40/F41).', data));
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
          if (/rotate/.test(inner.style.transform || '')) rotates = true;
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
