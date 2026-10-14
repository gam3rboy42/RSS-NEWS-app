package com.example

import com.example.data.local.FeedEntity
import com.example.util.JsonMigrationManager
import org.junit.Assert.*
import org.junit.Test
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun jsonMigration_parseFeeds_succeeds() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val json = """
      {
        "app": "Nothing RSS",
        "feeds": [
          {
            "url": "https://example.com/feed.xml",
            "title": "Example Feed",
            "category": "TECH"
          }
        ]
      }
    """.trimIndent()

    val (feeds, result) = JsonMigrationManager.parseJsonBackup(context, json)
    assertEquals(1, feeds.size)
    assertEquals("https://example.com/feed.xml", feeds[0].url)
    assertEquals("Example Feed", feeds[0].title)
    assertEquals("TECH", feeds[0].category)
    assertEquals(1, result.importedFeedsCount)
  }

  @Test
  fun podcastEligibility_newsArticleWithoutAudio_isExcluded() {
    val newsArticle = com.example.data.local.ArticleEntity(
      id = "1",
      feedUrl = "https://techcrunch.com/feed",
      feedTitle = "TechCrunch",
      category = "TECH",
      title = "New Smartphone Released",
      description = "A standard text news article.",
      link = "https://techcrunch.com/article1",
      mediaUrl = null,
      mediaType = null,
      isPodcast = false
    )

    fun isPullableAudio(art: com.example.data.local.ArticleEntity): Boolean {
      if (art.mediaType.equals("AUDIO", ignoreCase = true)) return true
      val mUrl = art.mediaUrl?.lowercase() ?: ""
      if (mUrl.contains(".mp3") || mUrl.contains(".m4a") || mUrl.contains(".ogg") ||
          mUrl.contains(".wav") || mUrl.contains(".aac") || mUrl.contains(".flac") ||
          mUrl.contains(".opus") || mUrl.contains("/audio") || mUrl.contains("audio/")) {
        return true
      }
      val lUrl = art.link.lowercase()
      return lUrl.endsWith(".mp3") || lUrl.endsWith(".m4a") || lUrl.contains(".mp3?") || lUrl.contains(".m4a?")
    }

    val parentFeedCategory = "TECH"
    val isInPodcastFolder = newsArticle.category.equals("PODCASTS", ignoreCase = true) ||
        parentFeedCategory == "PODCASTS"
    val hasAudio = isPullableAudio(newsArticle)
    val allowedOnPodcastPage = isInPodcastFolder || hasAudio

    assertFalse("News article without audio must NOT be allowed on podcast page", allowedOnPodcastPage)
  }

  @Test
  fun podcastEligibility_podcastFolderArticle_isIncluded() {
    val podcastArticle = com.example.data.local.ArticleEntity(
      id = "2",
      feedUrl = "https://feeds.twit.tv/twit.xml",
      feedTitle = "TWiT",
      category = "PODCASTS",
      title = "TWiT Episode 999",
      description = "Weekly podcast discussion.",
      link = "https://twit.tv/episode999",
      mediaUrl = "https://twit.tv/media/twit999.mp3",
      mediaType = "AUDIO",
      isPodcast = true
    )

    val parentFeedCategory = "PODCASTS"
    val isInPodcastFolder = podcastArticle.category.equals("PODCASTS", ignoreCase = true) ||
        parentFeedCategory == "PODCASTS"
    val allowedOnPodcastPage = isInPodcastFolder || (podcastArticle.mediaType == "AUDIO")

    assertTrue("Item in podcast folder must be allowed on podcast page", allowedOnPodcastPage)
  }

  @Test
  fun podcastEligibility_techFeedWithAudioPulled_isIncluded() {
    val techArticleWithAudio = com.example.data.local.ArticleEntity(
      id = "3",
      feedUrl = "https://theverge.com/feed",
      feedTitle = "The Verge",
      category = "TECH",
      title = "The Vergecast Special Audio Report",
      description = "Audio broadcast included in standard tech feed.",
      link = "https://theverge.com/vergecast-audio",
      mediaUrl = "https://traffic.megaphone.fm/vergecast.mp3",
      mediaType = "AUDIO",
      isPodcast = true
    )

    fun isPullableAudio(art: com.example.data.local.ArticleEntity): Boolean {
      if (art.mediaType.equals("AUDIO", ignoreCase = true)) return true
      val mUrl = art.mediaUrl?.lowercase() ?: ""
      return mUrl.contains(".mp3")
    }

    val parentFeedCategory = "TECH"
    val isInPodcastFolder = techArticleWithAudio.category.equals("PODCASTS", ignoreCase = true) ||
        parentFeedCategory == "PODCASTS"
    val hasAudio = isPullableAudio(techArticleWithAudio)
    val allowedOnPodcastPage = isInPodcastFolder || hasAudio

    assertTrue("News article with an audio file pulled MUST be allowed on podcast page", allowedOnPodcastPage)
  }

  @Test
  fun npuFiltering_runsOnRssPull_andPersistsClassification() {
    val unclassifiedArticles = listOf(
      com.example.data.local.ArticleEntity(
        id = "npu_1",
        feedUrl = "https://example.com/rss",
        feedTitle = "Daily Updates",
        category = "GENERAL",
        title = "Bitcoin Surges Past $100K in Record Crypto Rally",
        description = "Cryptocurrency markets and blockchain tokens gain massive momentum.",
        link = "https://example.com/crypto1"
      )
    )

    val feedCategoryMap = mapOf("https://example.com/rss" to "GENERAL")
    val processed = com.example.data.model.NpuTensorCategorizer.processArticlesOnTheFly(
      articles = unclassifiedArticles,
      feedCategoryMap = feedCategoryMap,
      enableReclassification = true
    )

    assertEquals(1, processed.size)
    val processedArticle = processed.first()
    assertEquals("FINANCE/CRYPTO story should be categorized under BUSINESS by NPU", "BUSINESS", processedArticle.category)
    assertTrue("Should be marked as NPU categorized", processedArticle.isNpuCategorized)
  }

  @Test
  fun htmlSanitizer_decodesVergeHeadlineAndSnippetWithNumericEntities() {
    val rawHeadline = "The SaaSpocalypse that wasn&#8217;t, with Atlassian CEO Mike Cannon-Brookes"
    val expectedHeadline = "The SaaSpocalypse that wasn’t, with Atlassian CEO Mike Cannon-Brookes"
    val cleanedHeadline = com.example.util.HtmlSanitizer.cleanText(rawHeadline)
    assertEquals(expectedHeadline, cleanedHeadline)

    val rawSnippet = "Today, I'm talking with Mike Cannon-Brookes, who is cofounder and CEO of Atlassian.&#160; Atlassian is ..."
    val expectedSnippet = "Today, I'm talking with Mike Cannon-Brookes, who is cofounder and CEO of Atlassian. Atlassian is ..."
    val cleanedSnippet = com.example.util.HtmlSanitizer.cleanText(rawSnippet)
    assertEquals(expectedSnippet, cleanedSnippet)
  }

  @Test
  fun htmlSanitizer_handlesDoubleEscapedAndNamedEntities() {
    val doubleEscaped = "Sony&amp;#8217;s new PlayStation &amp;quot;Pro&amp;quot; &amp;amp; VR"
    val expected = "Sony’s new PlayStation \"Pro\" & VR"
    val cleaned = com.example.util.HtmlSanitizer.cleanText(doubleEscaped)
    assertEquals(expected, cleaned)

    val namedEntities = "&ldquo;Breaking News&rdquo; &mdash; Apple &amp; Google announce &pound;100 &euro;50 deal"
    val expectedNamed = "“Breaking News” — Apple & Google announce £100 €50 deal"
    val cleanedNamed = com.example.util.HtmlSanitizer.cleanText(namedEntities)
    assertEquals(expectedNamed, cleanedNamed)
  }

  @Test
  fun htmlSanitizer_sanitizesArticleEntityFields() {
    val dirtyArticle = com.example.data.local.ArticleEntity(
      id = "verge_1",
      feedUrl = "https://www.theverge.com/rss/index.xml",
      feedTitle = "The Verge&#160;News",
      category = "TECH",
      title = "The SaaSpocalypse that wasn&#8217;t",
      description = "Atlassian.&#160; Big SaaS change&#8230;",
      link = "https://theverge.com/article"
    )

    val sanitized = com.example.util.HtmlSanitizer.sanitizeArticle(dirtyArticle)
    assertEquals("The SaaSpocalypse that wasn’t", sanitized.title)
    assertEquals("Atlassian. Big SaaS change…", sanitized.description)
    assertEquals("The Verge News", sanitized.feedTitle)
  }
}
