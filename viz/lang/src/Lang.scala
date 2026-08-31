package elmix.lang

import org.scalajs.dom
import scala.scalajs.js

/** SV/EN-vaxlare for de statiska sidorna. Sidan bar bada spraken i parade
  * lang-element och CSS doljer det som inte galler; det enda som behover kod ar
  * att satta html[data-lang], rita tva knappar och komma ihag valet.
  *
  * Medvetet utan Laminar: modulen ror tva knappar och ett attribut.
  */
object Lang:

  private val Key = "elmix-lang"
  private val Langs = List("sv", "en")

  /** Lagringen kan kasta i privat lage och nar webblasaren blockerar site data,
    * sa bade lasning och skrivning far falla tyst tillbaka pa sidans eget varde. */
  private def stored: Option[String] =
    try Option(dom.window.localStorage.getItem(Key)).filter(Langs.contains)
    catch case _: Throwable => None

  private def remember(l: String): Unit =
    try dom.window.localStorage.setItem(Key, l)
    catch case _: Throwable => ()

  private def current: String =
    stored
      .orElse(Option(dom.document.documentElement.getAttribute("data-lang")))
      .filter(Langs.contains)
      .getOrElse("sv")

  private def apply(l: String, buttons: List[dom.html.Button]): Unit =
    dom.document.documentElement.setAttribute("data-lang", l)
    dom.document.documentElement.setAttribute("lang", l)
    buttons.foreach(b => b.setAttribute("aria-pressed", (b.dataset("lang") == l).toString))
    remember(l)

  private def build(): Unit =
    val host = dom.document.getElementById("lang-switch")
    if host == null then () else
      host.innerHTML = ""
      var buttons = List.empty[dom.html.Button]
      buttons = Langs.map { l =>
        val b = dom.document.createElement("button").asInstanceOf[dom.html.Button]
        b.`type` = "button"
        b.textContent = l.toUpperCase
        b.dataset("lang") = l
        b.setAttribute("aria-label", if l == "sv" then "Svenska" else "English")
        host.appendChild(b)
        b
      }
      buttons.foreach(b => b.onclick = _ => apply(b.dataset("lang"), buttons))
      apply(current, buttons)   // sparat val vinner over sidans standard

  def main(args: Array[String]): Unit =
    if dom.document.readyState == "loading" then
      dom.document.addEventListener("DOMContentLoaded", (_: dom.Event) => build())
    else build()
