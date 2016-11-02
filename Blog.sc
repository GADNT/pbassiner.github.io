import $ivy.`com.lihaoyi::scalatags:0.6.0`
import $ivy.`com.atlassian.commonmark:commonmark:0.5.1`

import $file.GitHubClient, GitHubClient._

import ammonite.ops._

import java.text.SimpleDateFormat
import java.util.Calendar

// Cleanup
rm! cwd/"index.html"
rm! cwd/'blog

val utcFormatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
val monthYearDateFormatter = new SimpleDateFormat("MMMM yyyy")
val commentDateFormatter = new SimpleDateFormat("MMM dd, yyyy")

val currentDate = dateFormatter.format(Calendar.getInstance().getTime())

val postFiles = ls! cwd/'posts

val unsortedPosts = for(path <- postFiles) yield {
  val Array(date, postFilename, _) = path.last.split("\\.")
  (date, postFilename, path)
}

def mdNameToHtml(name: String) = {
  name.replace(" ", "-").toLowerCase + ".html"
}

def mdNameToTitle(name: String) = {
  name.replace("_", " ")
}

val sortedPosts = unsortedPosts.sortBy(_._1).reverse

object htmlContent {
  import scalatags.Text.all._

  val blogTitle = "Blog"

  val bootstrapCss = List(
    link(
      rel := "stylesheet",
      href := "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"
    ),
    link(
      rel := "stylesheet",
      href := "https://maxcdn.bootstrapcdn.com/font-awesome/4.6.3/css/font-awesome.min.css"
    )
  )

  val sidebar =
    div(`class` := "col-sm-3 col-sm-offset-1 blog-sidebar")(
      div(`class` := "sidebar-module sidebar-module-inset")(
        h4("About"),
        p("This is a personal blog. The opinions expressed here represent my own and not those of my employer."),
        p(strong("Pol Bassiner"), br, "Software Engineer", br, "Java & Scala developer", br, "CTO @ Netquest"),
        ul(`class` := "list-unstyled", style := "color: #000000; font-weight: bold",
          li(a(i(`class` := "fa fa-twitter-square"), " Twitter", href := "https://twitter.com/polbassiner", target := "_blank")),
          li(a(i(`class` := "fa fa-linkedin-square"), " LinkedIn", href := "https://es.linkedin.com/in/polbassiner", target := "_blank")),
          li(a(i(`class` := "fa fa-github-square"), " GitHub", href := "https://github.com/pbassiner", target := "_blank"))
        )
      )
    )

  val footerContent =
    footer(`class` := "blog-footer")(
      p("Last published on ", currentDate),
      p("This blog is hosted on ", a("GitHub", href := "https://github.com/"),
        ", built using ", a("Scala", href := "http://www.scala-lang.org/"), ", ",
        a("Ammonite", href := "https://github.com/lihaoyi/Ammonite"), ", and ",
        a("Bootstrap", href := "http://getbootstrap.com"),
        " (with ", a("Blog Theme", href := "http://getbootstrap.com/examples/blog/"),
        " by ", a("@mdo", href := "https://twitter.com/mdo"), ")."
      ),
      p("The strategy on building this blog was heavily inspired by ",
        a("Li Haoyi", href := "https://twitter.com/li_haoyi"),
        "'s blog post ", a("Scala Scripting and the 15 Minute Blog Engine",
        href := "http://www.lihaoyi.com/post/ScalaScriptingandthe15MinuteBlogEngine.html"), "."
      )
    )

    val commentsPostFooter = {
      import org.commonmark.html.HtmlRenderer
      import org.commonmark.parser.Parser

      val commentsPostFooterPath = cwd/'common/"footer.md"
      val parser = Parser.builder().build()
      val document = parser.parse(read! commentsPostFooterPath)
      val renderer = HtmlRenderer.builder().build()
      renderer.render(document)
    }

  println("POSTS")
  sortedPosts.foreach(println)

  for((postDate, postFilename, path) <- sortedPosts) {
    import org.commonmark.html.HtmlRenderer
    import org.commonmark.node._
    import org.commonmark.parser.Parser

    val postName = mdNameToTitle(postFilename)
    val parser = Parser.builder().build()
    val document = parser.parse(read! path)
    val renderer = HtmlRenderer.builder().build()
    val output = renderer.render(document)

    val gitHubIssue = issueHtmlUrl(postFilename)

    val postComments = commentsByPost(postFilename)

    val comments: scalatags.Text.Modifier = postComments.length match {
      case 0 => blockquote(p(`class` := "blog-comment")("There are no comments yet."))
      case _ => for ((author, comment, date) <- postComments)
        yield {
          val commentDate = commentDateFormatter.format(utcFormatter.parse(date))
          div(
            h4(`class` := "blog-comment-author")(author),
            p(`class` := "blog-comment-meta")(commentDate),
            blockquote(p(`class` := "blog-comment")(comment))
          )
        }
      }

    write(
      cwd/'blog/mdNameToHtml(postFilename),
      html(
        head(scalatags.Text.tags2.title(postName), bootstrapCss, link(rel := "stylesheet",  href := "../blog.css")),
        body(
          div(`class` := "container")(
            div(`class` := "blog-header")(
              h1(`class` := "blog-title")(a(blogTitle, href := "../index.html"))
            ),
            div(`class` := "row")(
              div(`class` := "col-sm-8 blog-main")(
                div(`class` := "blog-post")(
                  h2(`class` := "blog-post-title")(postName),
                  p(`class` := "blog-post-meta")(postDate),
                  raw(output),
                  raw(commentsPostFooter.replace("ISSUE_LINK", gitHubIssue)),
                  comments
                )
              ),
              sidebar
            )
          ),
          footerContent
        )
      ).render
    )
  }

  val HTML = {
    var currIndexMonth = ""
    html(
      head(scalatags.Text.tags2.title(blogTitle), bootstrapCss, link(rel := "stylesheet",  href := "blog.css")),
      body(
        div(`class` := "container")(
          div(`class` := "blog-header")(
            h1(`class` := "blog-title")(blogTitle)
          ),
          div(`class` := "row")(
            div(`class` := "col-sm-8 blog-main")(
              for((postDate, postFilename, _) <- sortedPosts)
              yield {

                val monthYearHeader = monthYearDateFormatter.format(dateFormatter.parse(postDate))
                val monthYearStyle = monthYearHeader match {
                  case s: String if s != currIndexMonth => "margin-bottom: 10em;"
                  case _ => "display: none;"
                }
                currIndexMonth = monthYearHeader

                div(
                  span(`class` := "blog-post-meta", style := monthYearStyle)(monthYearHeader),
                  h2(a(mdNameToTitle(postFilename), href := ("blog/" + mdNameToHtml(postFilename))))
                )
              }
            ),
            sidebar
          )
        ),
        footerContent
      )
    ).render
  }

}

write(cwd/"index.html", htmlContent.HTML)
