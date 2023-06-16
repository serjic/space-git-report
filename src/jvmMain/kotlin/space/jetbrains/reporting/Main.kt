package space.jetbrains.reporting

import kotlinx.datetime.*
import space.jetbrains.api.runtime.BatchInfo
import space.jetbrains.api.runtime.SpaceClient
import space.jetbrains.api.runtime.ktorClientForSpace
import space.jetbrains.api.runtime.resources.projects
import space.jetbrains.api.runtime.resources.teamDirectory
import space.jetbrains.api.runtime.types.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

const val totalMonths = 6

suspend fun main() {
    val httpClient = ktorClientForSpace()
    val client = SpaceClient(
        httpClient,
        "https://jetbrains.team",
        ""
    )

    val startDate = LocalDate(2023, 1, 1)

    val report = Report()

    (0 until totalMonths).forEach { month ->

        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
        val toDate = startDate.plus(month + 1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)

        val allCommits = allCommits(
            client,
            fromDate, toDate
        )

        allCommits
            .filter { it.authorProfile != null }
            .groupBy { it.author.name }
            .toList()
            .sortedByDescending { it.second.count() }
            .forEach {
                report.addCommits(
                    it.second.first().authorProfile!!,
                    fromDate,
                    it.second
                )
            }

    }

    client.teamDirectory.profiles.documents.createDocument(
        profile = ProfileIdentifier.Me,
        name = "123",
        folder = FolderIdentifier.Root,
        bodyIn = TextDocumentBodyCreateTypedIn(
            docContent = MdTextDocumentContent(
                markdown = report.md(startDate)
            )
        )
    )

}


class Report {
    private val commitInfoByUsername = mutableMapOf<String, ProfileCommitInfo>()

    fun addCommits(profile: TD_MemberProfile, fromDate: LocalDate, commits: List<GitCommitInfo>) {
        commitInfoByUsername.getOrPut(profile.username) {
            ProfileCommitInfo(profile)
        }.apply {
            this.addCommits(fromDate, commits)
        }
    }

    fun csv(startDate: LocalDate) = buildString {
        appendLine("```csv")
        append("Member,")
        appendLine(
            (0 until totalMonths).joinToString(separator = ",") { month ->
                val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                fromDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            }
        )

        commitInfoByUsername
            .toList()
            .sortedByDescending {
                it.second.total
            }
            .forEach {
                append("https://jetbrains.team/m/${it.second.profile.username},")
                val profileCommitInfo = it.second
                appendLine(
                    (0 until totalMonths).joinToString(separator = ",") { month ->
                        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                        val count = profileCommitInfo.byDate(fromDate) ?: 0
                        count.toString()
                    }
                )
            }
        appendLine("```")
    }

    fun md(startDate: LocalDate) = buildString {
        appendLine(
            "| Member | " + (0 until totalMonths).joinToString(separator = " | ") { month ->
                val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                fromDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            } + " |"
        )
        appendLine(
            "| ------ | " + (0 until totalMonths).joinToString(separator = " | ") {
                "------"
            } + " |"
        )

        commitInfoByUsername
            .toList()
            .sortedByDescending {
                it.second.total
            }
            .forEach {
                append("| https://jetbrains.team/m/${it.second.profile.username} | ")
                val profileCommitInfo = it.second
                appendLine(
                    (0 until totalMonths).joinToString(separator = " | ") { month ->
                        val fromDate = startDate.plus(month, DateTimeUnit.MONTH)
                        val toDate = startDate.plus(month + 1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
                        val count = profileCommitInfo.byDate(fromDate) ?: 0
                        val commitsLink =
                            "https://jetbrains.team/p/crl/repositories/space/commits?query=author%3A${profileCommitInfo.profile.username}%20date%3A${format(fromDate)}..${format(toDate)}%20no-merges%3Atrue&tab=changes"
                        "[${count}]($commitsLink)"
                    } + " |"
                )
            }
    }

    class ProfileCommitInfo(val profile: TD_MemberProfile) {
        private val commitsList = mutableMapOf<LocalDate, Int>()
        fun byDate(fromDate: LocalDate) = commitsList[fromDate]
        val total get() = commitsList.map { it.value }.sum()


        fun addCommits(fromDate: LocalDate, commits: List<GitCommitInfo>) {
            commitsList.getOrPut(fromDate) {
                commits.count()
            }
        }

    }


}


private suspend fun allCommits(
    client: SpaceClient,
    fromDate: LocalDate,
    toDate: LocalDate
): MutableList<GitCommitInfo> {
    var batch = BatchInfo(null, 1000)
    val allCommits = mutableListOf<GitCommitInfo>()


    while (true) {

        val res = client.projects.repositories.commits(
            project = ProjectIdentifier.Key("crl"),
            repository = "space",
            query = "date:${format(fromDate)}..${format(toDate)} no-merges:true",
            batchInfo = batch
        ) {
            defaultPartial()
            authorProfile {
                defaultPartial()
            }
        }

        allCommits.addAll(res.data)

        if (res.data.count() < 1000)
            break

        batch = BatchInfo(res.next, 1000)

    }
    return allCommits
}

fun format(date: LocalDate): String {
    val pattern = DateTimeFormatter.ofPattern("d-MMM-uuuu")
    return date.toJavaLocalDate().format(pattern)

}
