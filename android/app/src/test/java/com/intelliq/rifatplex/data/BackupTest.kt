package com.intelliq.rifatplex.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private val examId = ExamId.generate()

    private fun data(title: String, subtitle: String = "", apiKey: String = "", exam: String = examId) = AppData(
        examId = exam,
        doc = DocFields(title = title, subtitle = subtitle),
        questions = listOf(
            Question(id = "q1", text = "What is 2+2?", options = (0 until 4).map { Option("o$it", "option $it", it == 1) }),
        ),
        ai = AiSettings(apiKey = apiKey),
    )

    private fun entry(id: String, title: String, updated: Long, folderId: String? = null, apiKey: String = "") = HistoryEntry(
        id = id, title = title, subtitle = "", folderId = folderId, language = "en", questionCount = 1,
        createdAt = 100L, updatedAt = updated, hasAnswerKey = true, state = data(title, apiKey = apiKey),
    )

    private fun snapshot(exam: String, set: Int?, createdAt: Long) = AnswerKeySnapshot(
        examId = exam, setIndex = set, geomVersion = OmrGeometry.VERSION,
        items = listOf(KeyItem(1, setOf(0)), KeyItem(2, setOf(2))),
        marksPerQ = 1.0, negativePerWrong = 0.25, createdAt = createdAt,
    )

    private fun json(
        folders: List<Folder> = emptyList(), entries: List<HistoryEntry> = emptyList(),
        keys: List<AnswerKeySnapshot> = emptyList(),
    ) = Backup.toJson(folders, entries, keys, "me@example.com", 1234L)

    // ---------------------------------------------------------------- round trip

    @Test fun everythingComesBackFromTheFile() {
        val folders = listOf(Folder("f1", "Physics", 5L))
        val entries = listOf(entry("h1", "Mid term", 900L, folderId = "f1"), entry("h2", "Final", 800L))
        val keys = listOf(snapshot(examId, null, 77L), snapshot(examId, 2, 78L))
        val c = Backup.parse(json(folders, entries, keys))!!
        assertEquals(folders, c.folders)
        assertEquals(listOf("h1", "h2"), c.entries.map { it.id })
        assertEquals("f1", c.entries[0].folderId)
        assertNull(c.entries[1].folderId)
        assertEquals(900L, c.entries[0].updatedAt)
        assertEquals(100L, c.entries[0].createdAt)
        assertTrue(c.entries[0].hasAnswerKey)
        assertEquals("Mid term", c.entries[0].state.doc.title)
        assertEquals(examId, c.entries[0].state.examId)
        assertEquals(1, c.entries[0].state.questions.size)
        assertEquals("What is 2+2?", c.entries[0].state.questions[0].text)
        assertEquals(setOf("$examId.-", "$examId.2"), c.keys.map { it.storeKey }.toSet())
        assertEquals(0.25, c.keys[0].negativePerWrong, 0.0)
    }

    @Test fun bengaliTextSurvives() {
        val title = "বার্ষিক পরীক্ষা ২০২৬ – পদার্থবিজ্ঞান"
        val c = Backup.parse(json(entries = listOf(entry("h1", title, 1L))))!!
        assertEquals(title, c.entries[0].title)
        assertEquals(title, c.entries[0].state.doc.title)
    }

    @Test fun emptyBackupIsStillAValidBackup() {
        val c = Backup.parse(json())!!
        assertTrue(c.folders.isEmpty() && c.entries.isEmpty() && c.keys.isEmpty())
    }

    @Test fun theApiKeyNeverLeavesThePhone() {
        val text = json(entries = listOf(entry("h1", "Paper", 1L, apiKey = "AIzaSECRET-KEY-123")))
        assertFalse(text.contains("AIzaSECRET-KEY-123"))
        assertEquals("", Backup.parse(text)!!.entries[0].state.ai.apiKey)
    }

    // ---------------------------------------------------------------- reading a file

    @Test fun wrongOrBrokenFilesAreRejected() {
        assertNull(Backup.parse("not json at all"))
        assertNull(Backup.parse("{}"))
        assertNull(Backup.parse("""{"format":"something-else","papers":[]}"""))
        assertNull(Backup.parse(""))
    }

    @Test fun oneBrokenPaperDoesNotStopTheRest() {
        val o = org.json.JSONObject(json(entries = listOf(entry("h1", "Good", 1L), entry("h2", "Also good", 2L))))
        o.getJSONArray("papers").put(2, org.json.JSONObject().put("id", "bad").put("state", "{{{ not json"))
        val c = Backup.parse(o.toString())!!
        assertEquals(listOf("h1", "h2"), c.entries.map { it.id })
        o.getJSONArray("papers").put(org.json.JSONObject().put("title", "no id and no state"))
        assertEquals(2, Backup.parse(o.toString())!!.entries.size)
    }

    @Test fun brokenKeyStringsAreSkipped() {
        val o = org.json.JSONObject(json(keys = listOf(snapshot(examId, null, 5L))))
        o.getJSONArray("keys").put("garbage").put("")
        assertEquals(1, Backup.parse(o.toString())!!.keys.size)
    }

    // ---------------------------------------------------------------- zip / unpack

    @Test fun zipRoundTrip() {
        val text = json(entries = listOf(entry("h1", "Paper", 1L)))
        val bytes = Backup.zip(text)
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
        assertEquals(text, Backup.unpack(bytes))
        assertNotNull(Backup.parse(Backup.unpack(bytes)!!))
    }

    @Test fun aPlainJsonFileAlsoWorks() {
        val text = json(entries = listOf(entry("h1", "Paper", 1L)))
        assertEquals(text, Backup.unpack(text.toByteArray(Charsets.UTF_8)))
    }

    @Test fun zipWithoutBackupJsonIsRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("other.txt"))
            z.write("hello".toByteArray())
            z.closeEntry()
        }
        assertNull(Backup.unpack(out.toByteArray()))
    }

    @Test fun backupJsonInsideAFolderOfTheZipIsFound() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("intelliq/backup.json"))
            z.write("{\"x\":1}".toByteArray())
            z.closeEntry()
        }
        assertEquals("{\"x\":1}", Backup.unpack(out.toByteArray()))
    }

    @Test fun readCappedStopsAtTheLimit() {
        val data = ByteArray(1000) { 1 }
        assertEquals(1000, Backup.readCapped(ByteArrayInputStream(data), 1000)!!.size)
        assertNull(Backup.readCapped(ByteArrayInputStream(data), 999))
        assertEquals(0, Backup.readCapped(ByteArrayInputStream(ByteArray(0)), 10)!!.size)
    }

    // ---------------------------------------------------------------- merge (restore never deletes)

    @Test fun newPapersAreAddedAndLocalOnesStay() {
        val local = listOf(entry("a", "Local only", 10L))
        val r = mergeEntries(local, listOf(entry("b", "From backup", 5L)))
        assertEquals(setOf("a", "b"), r.entries.map { it.id }.toSet())
        assertEquals(1, r.changed)
    }

    @Test fun anOlderBackupCopyNeverReplacesTheLocalOne() {
        val r = mergeEntries(listOf(entry("a", "Local new", 20L)), listOf(entry("a", "Backup old", 10L)))
        assertEquals("Local new", r.entries.single().title)
        assertEquals(0, r.changed)
    }

    @Test fun aNewerBackupCopyReplacesTheLocalOne() {
        val r = mergeEntries(listOf(entry("a", "Local old", 10L)), listOf(entry("a", "Backup new", 20L)))
        assertEquals("Backup new", r.entries.single().title)
        assertEquals(1, r.changed)
    }

    @Test fun theSameCopyChangesNothing() {
        val e = entry("a", "Same", 10L)
        assertEquals(0, mergeEntries(listOf(e), listOf(e)).changed)
    }

    @Test fun restoringTwiceGivesTheSameResult() {
        val local = listOf(entry("a", "L", 10L))
        val backup = listOf(entry("a", "B", 20L), entry("b", "C", 5L))
        val once = mergeEntries(local, backup)
        val twice = mergeEntries(once.entries, backup)
        assertEquals(once.entries.map { it.id to it.title }, twice.entries.map { it.id to it.title })
        assertEquals(0, twice.changed)
    }

    @Test fun foldersAreAddedByIdOnly() {
        val local = listOf(Folder("f1", "Mine", 1L))
        val add = foldersToAdd(local, listOf(Folder("f1", "Renamed in backup", 2L), Folder("f2", "New", 3L), Folder("f2", "Dup", 4L)))
        assertEquals(listOf("f2"), add.map { it.id })
        assertEquals("New", add.single().name)
    }
}
