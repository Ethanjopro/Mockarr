"""Builds the Three Streets Studios site (public repo Ethanjopro/threestreets, GitHub Pages) from
docs/release/privacy-policy.md, so the published policy and the repo copy stay identical.

Usage: python3 scripts/privacy-site.py <checkout of Ethanjopro/threestreets>
Then commit and push that checkout; Pages serves /mockarr/privacy/ (docs/release/play-launch.md §2).
Needs the `markdown` package (pip install markdown).
"""
import markdown, re, sys, pathlib
site = pathlib.Path(sys.argv[1])
src = (pathlib.Path(__file__).resolve().parent.parent / "docs/release/privacy-policy.md").read_text()
(site / "mockarr/privacy").mkdir(parents=True, exist_ok=True)
body_md = src.split("\n---\n", 1)[1]
effective = re.search(r"\*Effective: ([^·]+?) ·", src).group(1).strip()
email = re.search(r"Contact: ([^*\s]+)", src).group(1).strip()
html = markdown.markdown(body_md, extensions=["tables"])
html = re.sub(r"(https://[^\s<)]+)", r'<a href="\1">\1</a>', html)
html = html.replace(email, f'<a href="mailto:{email}">{email}</a>')
CSS = """
:root{--bg:#F7F8FC;--surface:#FFFFFF;--ink:#171A26;--muted:#5B6072;--line:#DDE0EA;--accent:#3949AB;color-scheme:light}
@media (prefers-color-scheme:dark){:root{--bg:#10121A;--surface:#181B26;--ink:#E7E9F2;--muted:#A3A8BA;--line:#2A2E3D;--accent:#BAC3FF;color-scheme:dark}}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--ink);font:16px/1.6 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;-webkit-text-size-adjust:100%}
main{max-width:720px;margin:0 auto;padding:40px 16px 64px}
.studio{font-size:13px;font-weight:600;letter-spacing:.08em;text-transform:uppercase;color:var(--muted);text-decoration:none}
h1{font-size:34px;line-height:1.15;margin:8px 0 4px;letter-spacing:-.01em}
h2{font-size:20px;margin:36px 0 8px}
.meta{color:var(--muted);margin:0 0 24px}
a{color:var(--accent)}
.table{overflow-x:auto;border:1px solid var(--line);border-radius:12px;background:var(--surface)}
table{border-collapse:collapse;width:100%;font-size:15px}
th,td{text-align:left;vertical-align:top;padding:10px 12px;border-bottom:1px solid var(--line)}
tr:last-child td{border-bottom:0}
th{font-size:13px;color:var(--muted);font-weight:600}
li{margin:4px 0}
.card{display:block;background:var(--surface);border:1px solid var(--line);border-radius:16px;padding:20px;margin-top:24px}
.card h2{margin:0 0 4px}
.card p{margin:0 0 12px;color:var(--muted)}
.links a{margin-right:16px;font-weight:600}
footer{margin-top:48px;color:var(--muted);font-size:14px}
"""
def page(title, desc, inner):
    return f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title}</title><meta name="description" content="{desc}"><style>{CSS}</style></head>
<body><main>{inner}</main></body></html>
"""
html = html.replace("<table>", '<div class="table"><table>').replace("</table>", "</table></div>")
(site/"mockarr/privacy/index.html").write_text(page("Mockarr privacy policy", "How the Mockarr Android app handles data.",
    f'<a class="studio" href="../../">Three Streets Studios</a><h1>Mockarr privacy policy</h1>'
    f'<p class="meta">Effective {effective}</p>{html}'
    f'<footer>© 2026 Three Streets Studios · <a href="mailto:{email}">{email}</a></footer>'))
(site/"index.html").write_text(page("Three Streets Studios", "Three Streets Studios makes small apps for Android.",
    f'<span class="studio">Three Streets Studios</span><h1>Small apps for Android.</h1>'
    f'<div class="card"><h2>Mockarr</h2>'
    f'<p>Play back realistic road routes with Android\'s built-in mock location feature.</p>'
    f'<span class="links"><a href="mockarr/privacy/">Privacy policy</a></span></div>'
    f'<footer>Contact: <a href="mailto:{email}">{email}</a></footer>'))
(site/".nojekyll").write_text("")
print("built", effective, email)
