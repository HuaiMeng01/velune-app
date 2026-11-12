"""把 docs/*.md 合并编译为单文件 HTML（离线可读，带侧边导航）。"""
import re
import shutil
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"
OUT = DOCS / "Velune-方案总览.html"

CHAPTERS = [
    ("README.md", "总览"),
    ("01-竞品与架构参考.md", "竞品与架构参考"),
    ("02-API-Key-接入与安全.md", "API Key 接入与安全"),
    ("03-UI-设计规范.md", "UI 设计规范"),
    ("04-Agent-与插件系统.md", "Agent 与插件系统"),
    ("05-插件开发指南.md", "插件开发指南"),
    ("06-交付物-模块-选型-里程碑.md", "交付物与里程碑"),
    ("07-聊天页与AI能力矩阵.md", "聊天页与 AI 能力矩阵"),
    ("08-上下文与记忆系统.md", "上下文与记忆系统"),
    ("09-功能清单-待确认.md", "功能清单（待确认）"),
]

MD = markdown.Markdown(
    extensions=["extra", "toc", "sane_lists", "fenced_code", "codehilite", "admonition"],
    extension_configs={
        "toc": {"permalink": False},
        "codehilite": {"guess_lang": False, "css_class": "codehilite"},
    },
)


def convert(text: str) -> str:
    MD.reset()
    return MD.convert(text)


def fix_links(html_text: str, order: list[str]) -> str:
    for idx, (fname, _) in enumerate(CHAPTERS):
        html_text = html_text.replace(f'href="{fname}"', f'href="#ch{idx}"')
        html_text = html_text.replace(f"href='{fname}'", f'href="#ch{idx}"')
    return html_text


def wrap_tables(html_text: str) -> str:
    return html_text.replace("<table>", '<div class="tablewrap"><table>').replace(
        "</table>", "</table></div>"
    )


def build() -> None:
    nav_parts = []
    sections = []

    for idx, (fname, title) in enumerate(CHAPTERS):
        path = DOCS / fname
        raw = path.read_text(encoding="utf-8")
        body = fix_links(convert(raw), [c[0] for c in CHAPTERS])
        body = wrap_tables(body)

        subs = re.findall(r'<h2 id="([^"]*)">(.*?)</h2>', body)
        sub_items = "".join(
            f'<a class="nav-sub" href="#ch{idx}-{sid}">{re.sub("<[^>]+>", "", st)}</a>'
            for sid, st in subs
        )
        for sid, _ in subs:
            body = body.replace(f'<h2 id="{sid}">', f'<h2 id="ch{idx}-{sid}">')

        num = "00" if idx == 0 else f"{idx:02d}"
        nav_parts.append(
            f'<div class="nav-group"><a class="nav-chapter" href="#ch{idx}">'
            f'<span class="nav-num">{num}</span><span>{title}</span></a>{sub_items}</div>'
        )
        sections.append(
            f'<section class="chapter" id="ch{idx}">{body}</section>'
        )

    css = (ROOT / "tools" / "doc_theme.css").read_text(encoding="utf-8")
    html_doc = f"""<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Velune · Android Agent 聊天应用 产品与技术方案</title>
<style>{css}</style>
</head>
<body>
<aside class="sidebar">
  <div class="brand">
    <div class="brand-mark">V</div>
    <div class="brand-text">
      <strong>Velune</strong>
      <span>Android Agent 聊天应用</span>
    </div>
  </div>
  <nav class="nav">{''.join(nav_parts)}</nav>
  <div class="sidebar-foot">v1.0 · 2026-10-17</div>
</aside>
<main class="content">
  <div class="content-inner">
    {''.join(sections)}
    <footer class="doc-foot">本文档由 docs/*.md 自动编译生成，请勿直接编辑 HTML。</footer>
  </div>
</main>
<script>
(function () {{
  var links = Array.prototype.slice.call(document.querySelectorAll('.nav a'));
  var map = {{}};
  links.forEach(function (a) {{
    var id = a.getAttribute('href').slice(1);
    map[id] = a;
  }});
  var observer = new IntersectionObserver(function (entries) {{
    entries.forEach(function (e) {{
      if (e.isIntersecting) {{
        links.forEach(function (l) {{ l.classList.remove('active'); }});
        var a = map[e.target.id];
        if (a) {{
          a.classList.add('active');
          var group = a.closest('.nav-group');
          if (group) {{ links.forEach(function(l){{ if(l.closest('.nav-group')===group) l.classList.add('in-group'); }}); }}
        }}
      }}
    }});
  }}, {{ rootMargin: '-10% 0px -75% 0px', threshold: 0 }});
  document.querySelectorAll('.chapter, .chapter h2').forEach(function (el) {{ observer.observe(el); }});
}})();
</script>
</body>
</html>
"""
    OUT.write_text(html_doc, encoding="utf-8")
    size = OUT.stat().st_size / 1024
    print(f"built: {OUT}  ({size:.1f} KB)")


if __name__ == "__main__":
    build()
