package com.example.data.model

import com.example.data.local.ArticleEntity
import java.util.Locale

object SubcategoryAnalyzer {

    // Subcategory definitions per main category
    private val subcategoryRules = mapOf(
        "TECH" to listOf(
            "AI & CHIPS" to listOf("ai", "llm", "chip", "chips", "nvidia", "intel", "amd", "semiconductor", "tsmc", "arm", "gpu", "processor", "neural", "openal", "claude", "gemini"),
            "HARDWARE & GADGETS" to listOf("iphone", "pixel", "galaxy", "macbook", "laptop", "phone", "smartphone", "headset", "vision pro", "watch", "battery", "display", "gadget", "device", "hardware", "screen"),
            "SOFTWARE & APPS" to listOf("app", "apps", "software", "update", "os", "android", "ios", "windows", "macos", "linux", "browser", "chrome", "code", "developer", "api"),
            "STARTUPS & VENTURE" to listOf("startup", "startups", "raise", "raises", "funding", "series a", "series b", "venture", "vc", "founder", "acquisition", "acquired", "valuation"),
            "CYBERSECURITY & PRIVACY" to listOf("security", "cyber", "hack", "hacked", "hacker", "breach", "leak", "vulnerability", "privacy", "malware", "ransomware", "encryption", "password")
        ),
        "SCIENCE" to listOf(
            "SPACE & ASTRO" to listOf("space", "nasa", "spacex", "moon", "mars", "star", "stars", "planet", "planets", "galaxy", "orbit", "rocket", "astronomy", "telescope", "webb", "cosmos"),
            "HEALTH & MEDICINE" to listOf("health", "medicine", "cancer", "vaccine", "brain", "dna", "gene", "disease", "clinical", "doctor", "hospital", "patient", "drug", "biology"),
            "PHYSICS & ENERGY" to listOf("physics", "quantum", "energy", "fusion", "atom", "nuclear", "particle", "laser", "superconductor", "gravity"),
            "ENVIRONMENT & CLIMATE" to listOf("climate", "earth", "ocean", "species", "forest", "emissions", "carbon", "renewable", "solar", "wind", "environment")
        ),
        "GAMING" to listOf(
            "CONSOLES & PC" to listOf("ps5", "xbox", "nintendo", "switch", "pc", "playstation", "console", "steam", "deck", "rtx"),
            "REVIEWS & GUIDES" to listOf("review", "guide", "walkthrough", "preview", "rating", "best", "tips", "tricks"),
            "ESPORTS & INDUSTRY" to listOf("esports", "tournament", "league", "layoffs", "studio", "developer", "publisher", "sales", "xbox live", "game pass")
        ),
        "BUSINESS" to listOf(
            "MARKETS & STOCKS" to listOf("stock", "stocks", "market", "wall street", "dow", "s&p", "nasdaq", "fed", "inflation", "shares", "earnings", "dividend"),
            "FINANCE & CRYPTO" to listOf("crypto", "bitcoin", "ethereum", "blockchain", "bank", "banking", "interest rate", "fed", "treasury", "sec", "fintech"),
            "ECONOMY & COMMERCE" to listOf("economy", "gdp", "trade", "tariffs", "retail", "consumer", "sales", "real estate", "housing", "jobs")
        ),
        "DESIGN" to listOf(
            "UI/UX & WEB" to listOf("ui", "ux", "interface", "web", "css", "figma", "typography", "design system", "user experience", "frontend"),
            "ARCHITECTURE & ART" to listOf("architecture", "interior", "art", "building", "sculpture", "exhibition", "museum", "furniture"),
            "PRODUCT DESIGN" to listOf("product design", "industrial", "packaging", "material", "minimalism", "branding")
        ),
        "AI" to listOf(
            "LLMS & MODELS" to listOf("gpt", "gemini", "claude", "llama", "model", "prompt", "transformer", "reasoning", "benchmark"),
            "ROBOTICS & AUTONOMY" to listOf("robot", "robotics", "autonomous", "self-driving", "tesla", "humanoid", "drone"),
            "POLICY & ETHICS" to listOf("ethics", "safety", "policy", "regulation", "copyright", "deepfake", "watermark", "eu ai act")
        ),
        "WORLD" to listOf(
            "GLOBAL POLITICS" to listOf("election", "president", "minister", "parliament", "government", "policy", "summit", "un", "diplomacy"),
            "CULTURE & SOCIETY" to listOf("culture", "society", "art", "music", "film", "history", "community", "education")
        )
    )

    fun analyze(title: String, description: String, category: String): String {
        val textToAnalyze = "$title $description".lowercase(Locale.ROOT)
        val categoryUpper = category.uppercase(Locale.ROOT)

        val rulesForCategory = subcategoryRules[categoryUpper]
        if (rulesForCategory != null) {
            for ((subCatName, keywords) in rulesForCategory) {
                if (keywords.any { textToAnalyze.contains(it) }) {
                    return subCatName
                }
            }
        }

        // Cross-category fallback keyword lookup
        for ((_, rules) in subcategoryRules) {
            for ((subCatName, keywords) in rules) {
                if (keywords.any { textToAnalyze.contains(it) }) {
                    return subCatName
                }
            }
        }

        return "GENERAL"
    }

    fun getAllSubcategoriesForCategory(category: String): List<String> {
        val categoryUpper = category.uppercase(Locale.ROOT)
        if (categoryUpper == "ALL") {
            return listOf("ALL") + subcategoryRules.values.flatMap { rules -> rules.map { it.first } }.distinct()
        }
        val rules = subcategoryRules[categoryUpper]
        return if (rules != null) {
            listOf("ALL") + rules.map { it.first } + "GENERAL"
        } else {
            listOf("ALL", "GENERAL")
        }
    }
}
