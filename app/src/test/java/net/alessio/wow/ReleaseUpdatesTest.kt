package net.alessio.wow

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseUpdatesTest {
    private fun release(tag: String = "v0.10.0", draft: Boolean = false, prerelease: Boolean = false): JSONObject =
        JSONObject().put("tag_name", tag).put("draft", draft).put("prerelease", prerelease)

    private fun JSONObject.withAsset(
        name: String = "WoW-0.10.0-debug.apk",
        url: String = "https://github.com/alessio89g/WoW/releases/download/v0.10.0/$name",
        size: Long = 13_000_000
    ): String = put("assets", JSONArray().put(
        JSONObject().put("name", name).put("browser_download_url", url).put("size", size)
    )).toString()

    @Test fun picksNewerStableReleaseWithCompatibleAsset() {
        val update = ReleaseUpdates.parse(release().withAsset(), "0.9.0")
        assertEquals("v0.10.0", update?.version)
        assertEquals("WoW-0.10.0-debug.apk", update?.name)
    }

    @Test fun attachesNotesFromTheSameReleaseAndHandlesMissingNotes() {
        val notes = "## Changes\n- New license menu"
        val update = ReleaseUpdates.parse(release().put("body", notes).withAsset(), "0.9.0")
        assertEquals(notes, update?.changelog)
        val noNotes = ReleaseUpdates.parse(release().put("body", JSONObject.NULL).withAsset(), "0.9.0")
        assertEquals("", noNotes?.changelog)
    }

    @Test fun rejectsOlderDraftOrPrereleaseVersions() {
        assertNull(ReleaseUpdates.parse(release().withAsset(), "0.10.0"))
        assertNull(ReleaseUpdates.parse(release().withAsset(), "0.11.0"))
        assertNull(ReleaseUpdates.parse(release(draft = true).withAsset(), "0.9.0"))
        assertNull(ReleaseUpdates.parse(release(prerelease = true).withAsset(), "0.9.0"))
    }

    @Test fun rejectsWrongAssetAndOversizedDownload() {
        assertNull(ReleaseUpdates.parse(release().withAsset(name = "other.apk"), "0.9.0"))
        assertNull(ReleaseUpdates.parse(release().withAsset(url = "https://example.org/update.apk"), "0.9.0"))
        assertNull(ReleaseUpdates.parse(release().withAsset(size = 65L * 1024 * 1024), "0.9.0"))
    }
}
