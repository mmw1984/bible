package com.marcow.bible.feature.devotion.domain

import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Shared test support: the real WordPress markup saved from devotion.wkphc.org, copied out of
 * `legacy/flutter/test/fixtures/`, plus the few block-tree helpers every parser test needs.
 *
 * `legacy/flutter/` is read-only here, so the fixtures are duplicated rather than shared; they are the
 * one thing in this module that has to match byte for byte.
 */
internal fun fixture(name: String): String {
    val stream = checkNotNull(ClassLoader.getSystemResourceAsStream("fixtures/$name")) {
        "missing fixture $name"
    }
    return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
}

internal fun sectionsOf(blocks: List<DevotionBlock>): List<DevotionSection> = blocks.filterIsInstance<DevotionSection>()

internal fun sectionTitles(blocks: List<DevotionBlock>): List<String> = sectionsOf(blocks).map { it.title }

internal fun paragraphsOf(blocks: List<DevotionBlock>): List<DevotionParagraph> =
    blocks.filterIsInstance<DevotionParagraph>()

/** A blank paragraph or heading means the parser flushed an empty text buffer. */
internal fun assertNoEmptyText(blocks: List<DevotionBlock>) {
    for (block in blocks) {
        when (block) {
            is DevotionParagraph -> assertTrue(block.text.isNotEmpty(), "blank paragraph in $blocks")
            is DevotionHeading -> assertTrue(block.text.isNotEmpty(), "blank heading in $blocks")
            is DevotionSection -> assertNoEmptyText(block.blocks)
            else -> Unit
        }
    }
}
