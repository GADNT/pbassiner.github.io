import $ivy.`com.lihaoyi::scalatags:0.6.0`
import $ivy.`com.atlassian.commonmark:commonmark:0.5.1`

import $file.GitHubClient, GitHubClient._

import ammonite.ops._

import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar

import scala.collection.immutable.TreeMap

// Cleanup
rm ! pwd / "index.html"
rm ! pwd / 'blog

val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
val monthYearDateFormatter = new SimpleDateFormat("MMMM yyyy")
val monthYearDateFormatterForSorting = new SimpleDateFormat("yyyy-MM")
val commentDateFormatter = new SimpleDateFormat("MMM dd, yyyy")

val currentDate = dateFormatter.format(Calendar.getInstance().getTime())

val postFiles = ls ! pwd / 'posts

val unsortedPosts = for (
  path <- postFiles
) yield {
  val Array(date, postFilename, _) = path.last.split("\\.")
  (date, postFilename, path)
}

val sortedPosts = unsortedPosts.sortBy(_._1).reverse

def mdNameToHtml(name: String): String = {
  name.replace(" ", "-").toLowerCase + ".html"
}

def mdNameToTitle(name: String): String = {
  name.replace("_", " ")
}

def mdToHtml(content: String): String = {
  import org.commonmark.html.HtmlRenderer
  import org.commonmark.parser.Parser

  val parser = Parser.builder().build()
  val document = parser.parse(content)
  val renderer = HtmlRenderer.builder().build()
  renderer.render(document)
}

def mdFileToHtml(path: Path): String = mdToHtml(read ! path)

def first25WordsMdFileToHtml(path: Path): String = {
  val line = (read.lines ! path).filter(line => !line.isEmpty && line.charAt(0).toString.matches("[a-zA-Z]"))(0)
  val lineFirst25Words = line.split("\\s").take(25) mkString " "
  mdToHtml(s"$lineFirst25Words...")
}

def tweetPostUrl(postFilename: String): String = {
  val text = mdNameToTitle(postFilename)
  val url = s"https://pbassiner.github.io/blog/${mdNameToHtml(postFilename)}"
  return s"https://twitter.com/intent/tweet?text=${URLEncoder.encode(text, "UTF-8")}&url=${URLEncoder.encode(url, "UTF-8")}&via=polbassiner"
}

object htmlContent {

  import scalatags.Text.all._

  val blogTitle = "Blog"

  val bootstrapCss = List(
    link(
      rel := "stylesheet",
      href := "https://maxcdn.bootstrapcdn.com/bootstrap/4.0.0-alpha.5/css/bootstrap.min.css"
    ),
    link(
      rel := "stylesheet",
      href := "https://maxcdn.bootstrapcdn.com/font-awesome/4.6.3/css/font-awesome.min.css"
    )
  )

  val metaViewport = meta(name := "viewport", content := "width=device-width, initial-scale=1.0")

  val jQuery = script(`type` := "text/javascript", src := "http://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js")

  val sidebar =
    div(`class` := "col-sm-3 col-sm-offset-1 blog-sidebar")(
      div(`class` := "sidebar-module sidebar-module-inset")(
        h4("About"),
        p("This is a personal blog. The opinions expressed here represent my own and not those of my employer."),
        p(strong("Pol Bassiner"), br, "Software Engineer", br, "Java & Scala developer", br, "CTO @ Netquest"),
        ul(`class` := "list-unstyled about-social",
          li(a(i(`class` := "fa fa-twitter-square"), " Twitter", href := "https://twitter.com/polbassiner", target := "_blank")),
          li(a(i(`class` := "fa fa-linkedin-square"), " LinkedIn", href := "https://es.linkedin.com/in/polbassiner", target := "_blank")),
          li(a(i(`class` := "fa fa-github-square"), " GitHub", href := "https://github.com/pbassiner", target := "_blank"))
        )
      )
    )

  val footerContent = {
    val footerPath = pwd / 'common / "footer.md"
    val footerHtml = mdFileToHtml(footerPath)

    footer(`class` := "blog-footer")(
      raw(footerHtml.replace("CURRENT_DATE", currentDate))
    )
  }

  val postCommentsFooter = {
    val postCommentsFooterPath = pwd / 'common / "postCommentsFooter.md"
    mdFileToHtml(postCommentsFooterPath)
  }

  println("POSTS")
  sortedPosts.foreach(println)

  for ((postDate, postFilename, path) <- sortedPosts) {
    import org.commonmark.node._

    val postName = mdNameToTitle(postFilename)
    val (gitHubIssueUrl, gitHubCommentsUrl) = issueHtmlUrl(postFilename)
    val postContent = mdFileToHtml(path)

    val commentsJsScript = s"""
      <script type="text/javascript">
      $$(function() {
        $$.ajax({
          type: 'GET',
          url: "$gitHubCommentsUrl",
          headers: {Accept: "application/vnd.github.full+json"},
          success: function(data) {
            if (data.length === 0) {
              $$("#comments").append("
                <blockquote>
                  <p class='blog-comment'>There are no comments yet</p>
                </blockquote>
              ");
            } else {
              for (var i=0; i<data.length; i++) {
                $$("#comments").append("
                  <div>
                    <h4 class='blog-comment-author'>"+data[i].user.login+"</h4>
                    <p class='blog-comment-meta'>"+data[i].updated_at+"</p>
                    <blockquote><p class='blog-comment'>"+data[i].body_html+"</p></blockquote>
                  </div>
                ");
              }
            }
          }
        });
      });
      </script>
      """

    write(
      pwd / 'blog / mdNameToHtml(postFilename),
      html(
        head(scalatags.Text.tags2.title(postName), bootstrapCss, link(rel := "stylesheet", href := "../blog.css"), metaViewport, jQuery, raw(commentsJsScript)),
        body(
          div(`class` := "container")(
            div(`class` := "blog-header")(
              h1(`class` := "blog-title")(a(blogTitle, href := "../index.html"))
            ),
            div(`class` := "row")(
              div(`class` := "col-sm-8 blog-main")(
                div(`class` := "blog-post")(
                  h2(
                    a(
                      span(`class` := "blog-post-title")(postName),
                      span(`class` := "fa fa-twitter"),
                      `class` := "share-title",
                      href := tweetPostUrl(postFilename),
                      title := "Share",
                      target := "_blank"
                    )
                  ),
                  p(`class` := "blog-post-meta")(postDate),
                  div(`class` := "blog-post-body")(
                    raw(postContent),
                    raw(postCommentsFooter.replace("ISSUE_LINK", gitHubIssueUrl)),
                    div(id := "comments")
                  )
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

  val groupedPostsByMonth = sortedPosts.groupBy {
    case (postDate, postFilename, _) => monthYearDateFormatterForSorting.format(dateFormatter.parse(postDate))
  }

  val groupedPostsHtmlByMonth = groupedPostsByMonth.map {
    case (month, postList) => (month, postList map {
      case (postDate, postFilename, path) =>
        div(`class` := "card")(
          div(`class` := "card-block")(
            h4(`class` := "card-title")(
              a(mdNameToTitle(postFilename), href := ("blog/" + mdNameToHtml(postFilename)))
            ),
            raw(first25WordsMdFileToHtml(path)),
            a(`class` := "btn btn-primary btn-sm", "Read more", href := ("blog/" + mdNameToHtml(postFilename))),
            a(
              span(`class` := "fa fa-twitter"),
              `class` := "share",
              style := "float: right;",
              href := tweetPostUrl(postFilename),
              title := "Share",
              target := "_blank"
            )
          )
        )
    })
  }

  val groupedPostsHtml = TreeMap(groupedPostsHtmlByMonth.toArray:_*)(implicitly[Ordering[String]].reverse).map {
    case (month, postList) => div(
      span(`class` := "blog-post-meta")(monthYearDateFormatter.format(monthYearDateFormatterForSorting.parse(month))),
      postList
    )
  }.toList

  val HTML = {
    html(
      head(scalatags.Text.tags2.title(blogTitle), bootstrapCss, link(rel := "stylesheet", href := "blog.css"), metaViewport),
      body(
        div(`class` := "container")(
          div(`class` := "blog-header")(
            h1(`class` := "blog-title")(blogTitle)
          ),
          div(`class` := "row")(
            div(`class` := "col-sm-8 blog-main")(
              groupedPostsHtml
            ),
            sidebar
          )
        ),
        footerContent
      )
    ).render
  }

}

write(pwd / "index.html", htmlContent.HTML)
