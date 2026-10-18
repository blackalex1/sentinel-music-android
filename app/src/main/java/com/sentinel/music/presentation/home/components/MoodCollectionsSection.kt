package com.sentinel.music.presentation.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sentinel.music.data.model.TrackDto
import com.sentinel.music.presentation.home.models.DEFAULT_HOME_MOODS
import com.sentinel.music.presentation.home.models.HomeMoodItem

@Composable
fun MoodCollectionsSection(
    allTracks: List<TrackDto>,
    moods: List<HomeMoodItem> = DEFAULT_HOME_MOODS,
    onMoodClick: (String) -> Unit,
    onPlayMoodMix: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (moods.size < 4) return

    Column(modifier = modifier.padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            text = "Коллекции настроений",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Психоакустическая классификация фонотеки",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
        )

        // 2 строки по 2 карточки настроений
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MoodCollectionCard(
                mood = moods[0],
                count = allTracks.count { it.matchesMood(moods[0].key) },
                modifier = Modifier.weight(1f),
                onClick = { onMoodClick(moods[0].key) },
                onPlayClick = { onPlayMoodMix(moods[0].key) }
            )
            MoodCollectionCard(
                mood = moods[1],
                count = allTracks.count { it.matchesMood(moods[1].key) },
                modifier = Modifier.weight(1f),
                onClick = { onMoodClick(moods[1].key) },
                onPlayClick = { onPlayMoodMix(moods[1].key) }
            )
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MoodCollectionCard(
                mood = moods[2],
                count = allTracks.count { it.matchesMood(moods[2].key) },
                modifier = Modifier.weight(1f),
                onClick = { onMoodClick(moods[2].key) },
                onPlayClick = { onPlayMoodMix(moods[2].key) }
            )
            MoodCollectionCard(
                mood = moods[3],
                count = allTracks.count { it.matchesMood(moods[3].key) },
                modifier = Modifier.weight(1f),
                onClick = { onMoodClick(moods[3].key) },
                onPlayClick = { onPlayMoodMix(moods[3].key) }
            )
        }
    }
}
