package com.example.geckobrowser

import org.mozilla.geckoview.WebRequestError

/** The failures that get a themed local page instead of Gecko's built-in one. */
enum class ErrorKind {
    Offline,
    UnknownHost,
    Refused,
    Timeout,
    Certificate,
    Generic,
}

/**
 * Builds the small, self-contained HTML page GeckoView shows when a top-level
 * load fails. The page is handed back through `NavigationDelegate.onLoadError`,
 * so it keeps the failed URL in its address and can offer a real retry link.
 *
 * Certificate failures deliberately have no "continue anyway": the only way
 * forward is back. Everything is inlined because the page cannot fetch assets.
 */
object ErrorPages {

    /** The page for [error], or null when Gecko should handle the failure. */
    fun forError(url: String, error: WebRequestError): String? {
        val scheme = url.substringBefore(':').lowercase()
        if (scheme != "http" && scheme != "https") return null
        val kind = when (error.category) {
            WebRequestError.ERROR_CATEGORY_NETWORK -> networkKind(error.code)
            WebRequestError.ERROR_CATEGORY_SECURITY -> ErrorKind.Certificate
            else -> return null
        }
        return build(url, kind)
    }

    private fun networkKind(code: Int): ErrorKind = when (code) {
        WebRequestError.ERROR_OFFLINE -> ErrorKind.Offline
        WebRequestError.ERROR_UNKNOWN_HOST -> ErrorKind.UnknownHost
        WebRequestError.ERROR_CONNECTION_REFUSED -> ErrorKind.Refused
        WebRequestError.ERROR_NET_TIMEOUT -> ErrorKind.Timeout
        else -> ErrorKind.Generic
    }

    private fun build(url: String, kind: ErrorKind): String {
        val host = hostOf(url).ifBlank { "this site" }
        val copy = when (kind) {
            ErrorKind.Offline -> Copy(
                title = "You're offline",
                message = "horiZen can't reach the network right now. Check your connection — the page reloads by itself in a moment.",
            )
            ErrorKind.UnknownHost -> Copy(
                title = "Site not found",
                message = "There is no site at that address. The name may be misspelled, or the site may be gone.",
                hint = "Check the address for typos.",
            )
            ErrorKind.Refused -> Copy(
                title = "Connection refused",
                message = "The site is reachable but refused the connection. It may be down, or blocking this network.",
            )
            ErrorKind.Timeout -> Copy(
                title = "Taking too long",
                message = "The site didn't answer in time. It may be busy, slow, or blocking this network.",
            )
            ErrorKind.Certificate -> Copy(
                title = "Connection isn't secure",
                message = "The certificate for $host isn't valid, so horiZen stopped before anything was sent.",
                hint = "horiZen won't bypass a certificate warning. If you trust this site, its owner needs to fix the certificate.",
                retry = false,
            )
            ErrorKind.Generic -> Copy(
                title = "Couldn't load the page",
                message = "Something went wrong while loading $host.",
            )
        }

        val safeUrl = esc(url)
        val safeHost = esc(host)
        val retryButton = if (copy.retry) {
            """<a class="primary" href="$safeUrl">Try again</a>"""
        } else {
            ""
        }
        val backClass = if (copy.retry) "ghost" else "primary"
        val hintBlock = if (copy.hint.isBlank()) {
            ""
        } else {
            """<p class="hint">${esc(copy.hint)}</p>"""
        }

        return """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<title>${esc(copy.title)}</title>
<style>
:root{
  --ink:#EDEBF7;--muted:#9C97B8;--accent:#8B7BFF;--accent2:#4FC8C0;
  --card:rgba(255,255,255,.05);--line:rgba(255,255,255,.10);
}
*{box-sizing:border-box}
html,body{height:100%}
body{
  margin:0;padding:0;
  background:radial-gradient(120% 80% at 50% -10%, #241B45 0%, #141127 45%, #0A0913 100%);
  color:var(--ink);
  font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,system-ui,sans-serif;
  -webkit-font-smoothing:antialiased;
  display:flex;align-items:center;justify-content:center;
  min-height:100vh;
}
main{width:100%;max-width:420px;padding:32px 26px;text-align:center}
.mark{
  width:64px;height:64px;margin:0 auto 26px;border-radius:20px;
  background:linear-gradient(145deg,#8B7BFF 0%,#6C5CE7 48%,#4FC8C0 100%);
  box-shadow:0 18px 44px rgba(108,92,231,.42), inset 0 1px 0 rgba(255,255,255,.35);
  display:flex;align-items:center;justify-content:center;
}
.mark span{
  font-size:38px;line-height:1;font-weight:700;color:#fff;
  font-family:Georgia,"Times New Roman",serif;letter-spacing:-2px;
  transform:translateY(-1px);
}
.brand{
  font-size:15px;font-weight:600;letter-spacing:-.2px;margin:0 0 22px;
  background:linear-gradient(90deg,#B7A9FF,#8B7BFF 45%,#4FC8C0);
  -webkit-background-clip:text;background-clip:text;color:transparent;
}
h1{font-size:24px;line-height:1.25;margin:0 0 12px;letter-spacing:-.5px;font-weight:700}
p{margin:0;color:var(--muted);font-size:14px;line-height:1.6}
.addr{
  margin:20px auto 26px;padding:10px 14px;border-radius:12px;
  background:var(--card);border:1px solid var(--line);
  font-size:12.5px;color:var(--muted);
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap;
}
.actions{display:flex;gap:10px;justify-content:center;flex-wrap:wrap}
a,button{
  font:inherit;font-size:14px;font-weight:600;
  padding:12px 22px;border-radius:14px;border:1px solid transparent;
  text-decoration:none;cursor:pointer;
  -webkit-tap-highlight-color:transparent;
  transition:transform .12s ease,opacity .12s ease;
}
a:active,button:active{transform:scale(.97)}
.primary{
  background:linear-gradient(135deg,#8B7BFF,#6C5CE7);
  color:#fff;
  box-shadow:0 12px 26px rgba(108,92,231,.40);
}
.ghost{background:transparent;border-color:var(--line);color:var(--ink)}
.hint{margin-top:24px;font-size:12px;line-height:1.55;color:var(--muted);opacity:.85}
</style>
</head>
<body>
<main>
  <div class="mark"><span>h</span></div>
  <div class="brand">horiZen</div>
  <h1>${esc(copy.title)}</h1>
  <p>${esc(copy.message)}</p>
  <div class="addr">$safeHost</div>
  <div class="actions">
    $retryButton
    <button class="$backClass" onclick="if(history.length>1){history.back()}else{history.go(-1)}">Go back</button>
  </div>
  $hintBlock
</main>
</body>
</html>"""
    }

    private fun hostOf(url: String): String {
        val cleaned = url.removePrefix("https://").removePrefix("http://")
        return cleaned.substringBefore('/').substringBefore('?').substringBefore('#')
    }

    /** The wording for one failure kind; [retry] is false where a retry is futile. */
    private data class Copy(
        val title: String,
        val message: String,
        val hint: String = "",
        val retry: Boolean = true,
    )

    private fun esc(value: String): String = buildString(value.length + 16) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }
}
