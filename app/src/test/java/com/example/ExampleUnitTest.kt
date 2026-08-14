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
}
