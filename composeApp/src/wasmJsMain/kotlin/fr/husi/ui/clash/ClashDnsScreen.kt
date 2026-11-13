package fr.husi.ui.clash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.husi.compose.DropDownSelector
import fr.husi.compose.material3.Text
import fr.husi.remote.ClashDnsResponse
import fr.husi.ktx.blankAsNull
import fr.husi.resources.Res
import fr.husi.resources.clash_dns
import fr.husi.resources.clash_dns_additional
import fr.husi.resources.clash_dns_answers
import fr.husi.resources.clash_dns_authority
import fr.husi.resources.clash_dns_questions
import fr.husi.resources.clash_dns_status
import fr.husi.resources.clash_dns_ttl
import fr.husi.resources.clash_query
import fr.husi.resources.clash_query_name
import fr.husi.resources.clash_query_type
import fr.husi.resources.clash_unsupported
import fr.husi.ui.settings.SettingsPage
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ClashDnsScreen(
    state: ClashResourceState<ClashDnsResponse>,
    onQuery: (String, String) -> Unit,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("A") }

    SettingsPage(Res.string.clash_dns, modifier = modifier, onBackPress = onBackPress) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.clash_query_name)) },
                    singleLine = true,
                )
                DropDownSelector(
                    value = type,
                    onValueChange = { type = it },
                    textToValue = { it },
                    values = listOf("A", "AAAA", "CNAME", "MX", "TXT", "NS", "SOA", "SRV", "PTR", "HTTPS"),
                    label = { Text(stringResource(Res.string.clash_query_type)) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    enabled = name.isNotBlank() && type.isNotBlank() && !state.loading,
                    onClick = { onQuery(name, type) },
                ) {
                    Text(stringResource(Res.string.clash_query))
                }
            }
        }
        if (state.loading) {
            item {
                LinearWavyProgressIndicator()
            }
        }
        if (state.error.isNotEmpty()) {
            item {
                Text(
                    if (state.unsupported) {
                        stringResource(Res.string.clash_unsupported)
                    } else {
                        state.error
                    },
                    Modifier.padding(16.dp),
                )
            }
        }
        state.data?.let { response ->
            clashCard(key = "status") {
                Text(
                    stringResource(Res.string.clash_dns_status, response.status.toString()),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (response.questions.isNotEmpty()) {
                item {
                    DnsSectionTitle(stringResource(Res.string.clash_dns_questions))
                }
                for ((index, question) in response.questions.withIndex()) {
                    clashCard(key = "question-$index") {
                        Text(question.name, style = MaterialTheme.typography.titleSmall)
                        Text(dnsTypeNames[question.type] ?: question.type.toString())
                    }
                }
            }

            val sections = listOf(
                response.answers to Res.string.clash_dns_answers,
                response.authority to Res.string.clash_dns_authority,
                response.additional to Res.string.clash_dns_additional,
            )
            for ((sectionIndex, section) in sections.withIndex()) {
                val (records, label) = section
                if (records.isEmpty()) continue

                item {
                    DnsSectionTitle(stringResource(label))
                }
                for ((index, record) in records.withIndex()) {
                    clashCard(key = "$sectionIndex-$index") {
                        Text(record.name, style = MaterialTheme.typography.titleSmall)
                        val typeName = dnsTypeNames[record.type] ?: record.type.toString()
                        Text(
                            listOfNotNull(
                                typeName,
                                stringResource(Res.string.clash_dns_ttl, record.ttl.toString()),
                                record.data.blankAsNull(),
                            ).joinToString(" · "),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DnsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private val dnsTypeNames = mapOf(
    1 to "A",
    2 to "NS",
    5 to "CNAME",
    6 to "SOA",
    12 to "PTR",
    15 to "MX",
    16 to "TXT",
    28 to "AAAA",
    33 to "SRV",
    65 to "HTTPS",
)
