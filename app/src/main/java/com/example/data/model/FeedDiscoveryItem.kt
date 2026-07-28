package com.example.data.model

import com.example.data.local.FeedEntity

data class FeedDiscoveryItem(
    val title: String,
    val url: String,
    val category: String,
    val description: String,
    val isDefaultPreferred: Boolean = false
)

object DefaultFeedCatalog {

    val categories = listOf("ALL", "TECH", "SCIENCE", "GAMING", "WORLD", "BUSINESS", "DESIGN", "AI")

    val curatedFeeds = listOf(
        // TECH
        FeedDiscoveryItem(
            title = "TechCrunch",
            url = "https://techcrunch.com/feed/",
            category = "TECH",
            description = "Latest tech startup, venture capital, and gadget news.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "The Verge",
            url = "https://www.theverge.com/rss/index.xml",
            category = "TECH",
            description = "Covers the intersection of technology, science, art, and culture.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "Ars Technica",
            url = "https://feeds.arstechnica.com/arstechnica/index",
            category = "TECH",
            description = "In-depth analysis of tech, science, and policy.",
            isDefaultPreferred = false
        ),
        FeedDiscoveryItem(
            title = "Wired",
            url = "https://www.wired.com/feed/rss",
            category = "TECH",
            description = "How technology is changing every aspect of human life.",
            isDefaultPreferred = false
        ),
        FeedDiscoveryItem(
            title = "Hacker News",
            url = "https://news.ycombinator.com/rss",
            category = "TECH",
            description = "Computer science, programming, and entrepreneurship news.",
            isDefaultPreferred = false
        ),

        // SCIENCE
        FeedDiscoveryItem(
            title = "NASA Breaking News",
            url = "https://www.nasa.gov/news-release/feed/",
            category = "SCIENCE",
            description = "Discover space exploration, aeronautics, and astronomical discoveries.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "ScienceDaily",
            url = "https://www.sciencedaily.com/rss/all.xml",
            category = "SCIENCE",
            description = "Latest research news in science, health, and environment.",
            isDefaultPreferred = false
        ),
        FeedDiscoveryItem(
            title = "Phys.org",
            url = "https://phys.org/rss-feed/",
            category = "SCIENCE",
            description = "Physics, nanotechnology, earth science, and space news.",
            isDefaultPreferred = false
        ),

        // GAMING
        FeedDiscoveryItem(
            title = "GameSpot",
            url = "https://www.gamespot.com/feeds/news/",
            category = "GAMING",
            description = "Video game news, reviews, trailers, and gaming coverage.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "IGN Video Games",
            url = "https://feeds.feedburner.com/ign/news",
            category = "GAMING",
            description = "Console, PC, and indie game announcements and reviews.",
            isDefaultPreferred = false
        ),
        FeedDiscoveryItem(
            title = "Polygon",
            url = "https://www.polygon.com/rss/index.xml",
            category = "GAMING",
            description = "Gaming culture, entertainment news, and reviews.",
            isDefaultPreferred = false
        ),

        // WORLD
        FeedDiscoveryItem(
            title = "BBC News Top Stories",
            url = "http://feeds.bbci.co.uk/news/rss.xml",
            category = "WORLD",
            description = "Global breaking news, world updates, and international reporting.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "NPR News",
            url = "https://feeds.npr.org/1001/rss.xml",
            category = "WORLD",
            description = "Independent journalism, audio stories, and national reporting.",
            isDefaultPreferred = false
        ),
        FeedDiscoveryItem(
            title = "Al Jazeera English",
            url = "https://www.aljazeera.com/xml/rss/all.xml",
            category = "WORLD",
            description = "International perspectives and breaking world news.",
            isDefaultPreferred = false
        ),

        // BUSINESS
        FeedDiscoveryItem(
            title = "CNBC World Business",
            url = "https://www.cnbc.com/id/100003114/device/rss/rss.html",
            category = "BUSINESS",
            description = "Markets, finance, economy, and corporate news updates.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "Forbes Top Stories",
            url = "https://www.forbes.com/real-time/feed2/",
            category = "BUSINESS",
            description = "Entrepreneurship, leadership, money, and wealth insights.",
            isDefaultPreferred = false
        ),

        // DESIGN
        FeedDiscoveryItem(
            title = "Design Milk",
            url = "https://design-milk.com/feed/",
            category = "DESIGN",
            description = "Modern design, architecture, interior, technology and art.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "Smashing Magazine",
            url = "https://www.smashingmagazine.com/feed/",
            category = "DESIGN",
            description = "Web design, UI/UX, CSS, typography, and frontend development.",
            isDefaultPreferred = false
        ),

        // AI
        FeedDiscoveryItem(
            title = "MIT Tech Review AI",
            url = "https://www.technologyreview.com/topic/artificial-intelligence/feed",
            category = "AI",
            description = "Deep research and coverage of artificial intelligence and machine learning.",
            isDefaultPreferred = true
        ),
        FeedDiscoveryItem(
            title = "VentureBeat AI",
            url = "https://venturebeat.com/category/ai/feed/",
            category = "AI",
            description = "Enterprise artificial intelligence news and breakthrough technology.",
            isDefaultPreferred = false
        )
    )

    fun toDefaultEntities(): List<FeedEntity> {
        return curatedFeeds.map { item ->
            FeedEntity(
                url = item.url,
                title = item.title,
                category = item.category,
                description = item.description,
                isPreferred = item.isDefaultPreferred,
                isEnabled = true,
                isCustom = false
            )
        }
    }
}
