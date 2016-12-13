import $ivy.`com.lihaoyi::scalatags:0.6.0`

import ammonite.ops._
import ammonite.ops.Internals.Writable
import java.text.SimpleDateFormat
import java.util.Calendar
import scala.collection.immutable.TreeMap
import scalatags.Text.all._

import $file.Config, Config._
import $file.GitHubClient, GitHubClient._
import $file.MdToHtml, MdToHtml._
import $file.Twitter, Twitter._

object DateUtils {
  val dateFormatter = new SimpleDateFormat("yyyy-MM-dd")
  val monthYearDateFormatter = new SimpleDateFormat("MMMM yyyy")
  val yearMonthDateFormatter = new SimpleDateFormat("yyyy-MM")
  val commentDateFormatter = new SimpleDateFormat("MMM dd, yyyy")

  val currentDate = dateFormatter.format(Calendar.getInstance().getTime())

  def yearMonthDayToYearMonth(date: String): String =
    yearMonthDateFormatter.format(dateFormatter.parse(date))
  def yearMonthToMonthYear(date: String): String =
    monthYearDateFormatter.format(yearMonthDateFormatter.parse(date))
}

object Common {
  import DateUtils._

  val blogTitle = "Blog"

  val bootstrapCss = List(
    link(rel := "stylesheet", href := "https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css"),
    link(rel := "stylesheet", href := "https://maxcdn.bootstrapcdn.com/font-awesome/4.6.3/css/font-awesome.min.css")
  )

  val metaViewport = meta(name := "viewport", content := "width=device-width, initial-scale=1.0")

  val jQuery = script(`type` := "text/javascript", src := "https://ajax.googleapis.com/ajax/libs/jquery/1/jquery.min.js")

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

}

object Builder {

  import DateUtils._, Common._
  import scalatags.Text.all._

  final case class Post(date: String, filename: String, path: Path)

  def cleanup = {
    rm ! pwd / "index.html"
    rm ! pwd / 'blog
  }

  def sortedPosts = {
    val postFiles = ls ! pwd / 'posts
    val unsortedPosts = for (
      path <- postFiles
    ) yield {
      val Array(date, postFilename, _) = path.last.split("\\.")
      Post(date, postFilename, path)
    }

    unsortedPosts.sortBy(_.date).reverse
  }

  def postCommentsFooter(gitHubIssueHtmlUrl: String) = {
    val postCommentsFooterPath = pwd / 'common / "postCommentsFooter.md"
    mdFileToHtml(postCommentsFooterPath).replace("ISSUE_LINK", gitHubIssueHtmlUrl)
  }

  def writePosts(config: Configuration) = {
    for (post <- sortedPosts) {
      val postName = mdFilenameToTitle(post.filename)
      val gitHubIssue = config.gitHubIntegration match {
        case Enabled => getGitHubIssueByPost(post.filename)
        case Disabled => GitHubIssue.empty
      }
      val postContent = mdFileToHtml(post.path)

      write(
        pwd / 'blog / mdFilenameToHtmlFilename(post.filename),
        html(
          head(
            scalatags.Text.tags2.title(postName),
            bootstrapCss,
            link(rel := "stylesheet", href := "../blog.css"),
            metaViewport,
            jQuery,
            raw(gitHubIssue.fetchCommentsAndAppendJs)
          ),
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
                        href := tweetPostUrl(post.filename),
                        title := "Share",
                        target := "_blank"
                      )
                    ),
                    p(`class` := "blog-post-meta")(post.date),
                    div(`class` := "blog-post-body")(
                      raw(postContent),
                      raw(postCommentsFooter(gitHubIssue.htmlUrl)),
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
  }

  def indexSortedPostsList = {
    def logPosts(groupedPostsByMonth: Map[String, Iterable[Post]]): Unit = {
      println("POSTS")
      groupedPostsByMonth.foreach {
        case (yearMonth, postList) => {
          println(yearMonth)
          postList foreach {
            case post: Post => println(s"\t$post")
          }
        }
      }
    }

    val groupedPostsByMonth = sortedPosts.groupBy {
      case post:Post => yearMonthDayToYearMonth(post.date)
    }

    logPosts(groupedPostsByMonth)

    val groupedPostsHtmlByMonth = groupedPostsByMonth.map {
      case (yearMonth, postList) => (yearMonth, postList map {
        case post: Post =>
          div(`class` := "row")(
            div(`class` := "col-sm-6 col-md-12")(
              div(`class` := "thumbnail")(
                div(`class` := "caption")(
                  h3(a(mdFilenameToTitle(post.filename), href := ("blog/" + mdFilenameToHtmlFilename(post.filename)))),
                  raw(mdFileFirst25WordsToHtml(post.path)),
                  a(`class` := "btn btn-primary btn-sm", "Read more", href := ("blog/" + mdFilenameToHtmlFilename(post.filename))),
                  a(
                    span(`class` := "fa fa-twitter"),
                    `class` := "share",
                    style := "float: right;",
                    href := tweetPostUrl(post.filename),
                    title := "Share",
                    target := "_blank"
                  )
                )
              )
            )
          )
      })
    }

    TreeMap(groupedPostsHtmlByMonth.toArray: _*)(implicitly[Ordering[String]].reverse).map {
      case (yearMonth, postList) => div(
        span(`class` := "blog-post-meta")(yearMonthToMonthYear(yearMonth)),
        postList
      )
    }.toList
  }

  def index =
    html(
      head(
        scalatags.Text.tags2.title(blogTitle),
        bootstrapCss,
        link(rel := "stylesheet", href := "blog.css"),
        metaViewport
      ),
      body(
        div(`class` := "container")(
          div(`class` := "blog-header")(
            h1(`class` := "blog-title")(blogTitle)
          ),
          div(`class` := "row")(
            div(`class` := "col-sm-8 blog-main")(
              indexSortedPostsList
            ),
            sidebar
          )
        ),
        footerContent
      )
    )

  def apply(config: Configuration): Writable = {
    cleanup
    writePosts(config)
    index.render
  }

}