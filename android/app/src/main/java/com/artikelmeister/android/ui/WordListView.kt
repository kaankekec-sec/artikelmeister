package com.artikelmeister.android.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.artikelmeister.android.Artikel
import com.artikelmeister.android.CardStage
import com.artikelmeister.android.DeckStore
import com.artikelmeister.android.Flashcard
import com.artikelmeister.android.GeminiAuthManager
import com.artikelmeister.android.SpeechManager
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WordListView(
    store: DeckStore,
    authManager: GeminiAuthManager,
    onOpenDrawer: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    val context = LocalContext.current

    var searchText by remember { mutableStateOf("") }
    var selectedDeckFilter by remember { mutableStateOf<String?>(null) }
    var selectedArtikelFilter by remember { mutableStateOf<Artikel?>(null) }
    var selectedStageFilter by remember { mutableStateOf<CardStage?>(null) }

    // Manual new word form state
    var newArtikel by remember { mutableStateOf(Artikel.DER) }
    var newWord by remember { mutableStateOf("") }
    var newMeaning by remember { mutableStateOf("") }
    var newDeckName by remember { mutableStateOf("Genel") }

    // Editing word & deck creation state
    var editingCard by remember { mutableStateOf<Flashcard?>(null) }
    var showNewDeckSheet by remember { mutableStateOf(false) }
    var deckToRename by remember { mutableStateOf<String?>(null) }

    var alertMessage by remember { mutableStateOf<String?>(null) }
    var showAlert by remember { mutableStateOf(false) }
    var showAIGenerator by remember { mutableStateOf(false) }

    var showNewWordDeckMenu by remember { mutableStateOf(false) }
    var showArtikelFilterMenu by remember { mutableStateOf(false) }
    var showStageFilterMenu by remember { mutableStateOf(false) }

    val filteredCards = remember(
        store.cards,
        selectedDeckFilter,
        selectedArtikelFilter,
        selectedStageFilter,
        searchText
    ) {
        store.cards.filter { card ->
            if (selectedDeckFilter != null && card.deckName != selectedDeckFilter) return@filter false
            if (selectedArtikelFilter != null && card.artikel != selectedArtikelFilter) return@filter false
            if (selectedStageFilter != null && card.stage != selectedStageFilter) return@filter false
            val q = searchText.trim().lowercase(Locale("tr", "TR"))
            if (q.isNotEmpty()) {
                val matchesWord = card.word.lowercase(Locale("tr", "TR")).contains(q)
                val matchesMeaning = card.meaning.lowercase(Locale("tr", "TR")).contains(q)
                val matchesArtikel = card.artikel.rawValue.lowercase(Locale.ROOT).contains(q)
                val matchesDeck = card.deckName.lowercase(Locale("tr", "TR")).contains(q)
                return@filter matchesWord || matchesMeaning || matchesArtikel || matchesDeck
            }
            true
        }
    }

    val importCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val targetDeck = selectedDeckFilter ?: newDeckName
                val res = store.importCSVFromUri(uri, defaultDeckName = targetDeck)
                alertMessage = """
                    Yeni eklenen: ${res.addedCount}
                    Güncellenen: ${res.updatedCount}
                    Atlanan (mevcut): ${res.skippedCount}
                    Geçersiz satır: ${res.invalidLines}
                """.trimIndent()
                showAlert = true
            }.onFailure { err ->
                alertMessage = err.localizedMessage ?: "CSV okunamadı."
                showAlert = true
            }
        }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            runCatching {
                val csvContent = store.exportCSVString()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csvContent.toByteArray(Charsets.UTF_8))
                }
                alertMessage = "Kelimeler başarıyla CSV olarak dışa aktarıldı."
                showAlert = true
            }.onFailure { err ->
                alertMessage = "Dışa aktarma hatası: ${err.localizedMessage}"
                showAlert = true
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.windowBackground)
    ) {
        // Top Toolbar: Header + Quick Add + Deck Chips + Search/Filters
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.controlBackground)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Row 1: Title + Main Actions
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                if (onOpenDrawer != null) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Menü",
                            tint = colors.primaryText
                        )
                    }
                }

                Text(
                    text = "Kelime Havuzu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )

                Text(
                    text = "${store.cards.size} kelime",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accentBlue,
                    modifier = Modifier
                        .background(colors.accentBlue.copy(alpha = 0.15f), CircleShape)
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                )

                Button(
                    onClick = { showAIGenerator = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentPurple,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("AI Kelime Üret", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = { importCsvLauncher.launch(arrayOf("text/*", "application/csv", "*/*")) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("CSV İçe Aktar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { exportCsvLauncher.launch("almanca_kelimeler.csv") },
                    enabled = store.cards.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.cardBorderSubtle),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = null,
                        tint = colors.primaryText,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("CSV Dışa Aktar", fontSize = 12.sp, color = colors.primaryText)
                }
            }

            // Row 2: Quick manual word entry bar
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Segmented Artikel Picker
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.textBackground)
                        .border(1.dp, colors.cardBorderSubtle, RoundedCornerShape(8.dp))
                ) {
                    Artikel.entries.forEach { art ->
                        val isSelected = newArtikel == art
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(36.dp)
                                .padding(2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) art.color else Color.Transparent)
                                .clickable { newArtikel = art }
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = art.rawValue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else colors.primaryText
                            )
                        }
                    }
                }

                // German Word Input
                CompactTextField(
                    value = newWord,
                    onValueChange = { newWord = it },
                    placeholder = "Almanca kelime (örn: Tisch)",
                    modifier = Modifier
                        .widthIn(min = 140.dp)
                        .weight(1f)
                )

                // Turkish Meaning Input
                CompactTextField(
                    value = newMeaning,
                    onValueChange = { newMeaning = it },
                    placeholder = "Türkçe anlamı / açıklama",
                    modifier = Modifier
                        .widthIn(min = 140.dp)
                        .weight(1f)
                )

                // Deck Selector Menu for New Word
                Box {
                    Surface(
                        color = colors.textBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable { showNewWordDeckMenu = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = colors.accentBlue,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = newDeckName,
                                fontSize = 12.sp,
                                color = colors.primaryText,
                                maxLines = 1
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showNewWordDeckMenu,
                        onDismissRequest = { showNewWordDeckMenu = false }
                    ) {
                        store.allDeckNames.forEach { deck ->
                            DropdownMenuItem(
                                text = { Text(deck) },
                                trailingIcon = {
                                    if (newDeckName == deck) {
                                        Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                },
                                onClick = {
                                    newDeckName = deck
                                    showNewWordDeckMenu = false
                                }
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Yeni Deste Ekle...") },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                            onClick = {
                                showNewWordDeckMenu = false
                                showNewDeckSheet = true
                            }
                        )
                    }
                }

                // Add Button
                Button(
                    onClick = {
                        if (store.addCard(newArtikel, newWord, newMeaning, newDeckName)) {
                            newWord = ""
                            newMeaning = ""
                        }
                    },
                    enabled = newWord.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentBlue,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ekle", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Row 3: Decks Bar with "+ Yeni Deste Ekle" button right alongside decks
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                DeckFilterChip(
                    title = "Tüm Desteler",
                    count = store.cards.size,
                    isSelected = selectedDeckFilter == null,
                    onClick = { selectedDeckFilter = null }
                )

                store.allDeckNames.forEach { deck ->
                    var showChipMenu by remember { mutableStateOf(false) }
                    Box {
                        DeckFilterChip(
                            title = deck,
                            count = store.cardCount(deck),
                            isSelected = selectedDeckFilter == deck,
                            onClick = {
                                selectedDeckFilter = deck
                                newDeckName = deck
                            },
                            onLongClick = { showChipMenu = true }
                        )

                        DropdownMenu(
                            expanded = showChipMenu,
                            onDismissRequest = { showChipMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Deste Adını Değiştir...") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showChipMenu = false
                                    deckToRename = deck
                                }
                            )
                            if (deck != "Genel") {
                                DropdownMenuItem(
                                    text = { Text("Desteyi Sil (Kelimeleri 'Genel'e Taşı)", color = colors.vividRed) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.vividRed)
                                    },
                                    onClick = {
                                        showChipMenu = false
                                        store.deleteDeck(name = deck, deleteWords = false)
                                        if (selectedDeckFilter == deck) {
                                            selectedDeckFilter = null
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // "+ Yeni Deste Ekle" Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.accentBlue.copy(alpha = 0.15f))
                        .border(1.dp, colors.accentBlue.copy(alpha = 0.35f), CircleShape)
                        .clickable { showNewDeckSheet = true }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = colors.accentBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Yeni Deste Ekle",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentBlue
                    )
                }
            }

            // Row 4: Search & Artikel / Stage Filters
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Search Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.textBackground)
                        .border(1.dp, colors.cardBorderSubtle, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchText.isEmpty()) {
                            Text(
                                text = "Kelime, anlam veya deste ara...",
                                fontSize = 12.sp,
                                color = colors.secondaryText.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                color = colors.primaryText
                            ),
                            cursorBrush = SolidColor(colors.accentBlue),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchText.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Temizle",
                            tint = colors.secondaryText,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable { searchText = "" }
                        )
                    }
                }

                // Artikel Filter Dropdown
                Box {
                    Surface(
                        color = colors.textBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { showArtikelFilterMenu = true }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = selectedArtikelFilter?.rawValue ?: "Tüm Artikeller",
                                fontSize = 12.sp,
                                color = colors.primaryText
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showArtikelFilterMenu,
                        onDismissRequest = { showArtikelFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tüm Artikeller") },
                            onClick = {
                                selectedArtikelFilter = null
                                showArtikelFilterMenu = false
                            }
                        )
                        Artikel.entries.forEach { art ->
                            DropdownMenuItem(
                                text = { Text(art.rawValue) },
                                onClick = {
                                    selectedArtikelFilter = art
                                    showArtikelFilterMenu = false
                                }
                            )
                        }
                    }
                }

                // Stage Filter Dropdown
                Box {
                    Surface(
                        color = colors.textBackground,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, colors.cardBorderSubtle),
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { showStageFilterMenu = true }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = selectedStageFilter?.title ?: "Tüm Durumlar",
                                fontSize = 12.sp,
                                color = colors.primaryText
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showStageFilterMenu,
                        onDismissRequest = { showStageFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Tüm Durumlar") },
                            onClick = {
                                selectedStageFilter = null
                                showStageFilterMenu = false
                            }
                        )
                        CardStage.entries.forEach { stg ->
                            DropdownMenuItem(
                                text = { Text(stg.title) },
                                onClick = {
                                    selectedStageFilter = stg
                                    showStageFilterMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = colors.cardBorderSubtle)

        // Word Table List
        if (filteredCards.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(42.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (selectedDeckFilter != null) {
                    Text(
                        text = "'$selectedDeckFilter' destesinde henüz kelime yok.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondaryText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Yukarıdaki ekleme çubuğundan veya Gemini AI ile bu desteye yeni kelimeler ekleyebilirsiniz.",
                        fontSize = 12.sp,
                        color = colors.secondaryText
                    )
                } else {
                    Text(
                        text = if (store.cards.isEmpty()) "Henüz kayıtlı kelime yok."
                        else "Arama kriterlerine uygun kelime bulunamadı.",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondaryText
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(filteredCards, key = { _, card -> card.id }) { index, card ->
                    WordRowItem(
                        card = card,
                        index = index,
                        dueText = dueDescription(card, store.currentTime),
                        onSpeak = { SpeechManager.speak(card.fullGermanTerm) },
                        onEdit = { editingCard = card },
                        onDelete = { store.deleteCard(card.id) }
                    )
                    HorizontalDivider(color = colors.cardBorderSubtle.copy(alpha = 0.4f))
                }
            }
        }
    }

    if (showAIGenerator) {
        AIGeneratorDialog(
            store = store,
            authManager = authManager,
            onDismiss = { showAIGenerator = false }
        )
    }

    editingCard?.let { cardToEdit ->
        EditWordDialog(
            store = store,
            card = cardToEdit,
            onDismiss = { editingCard = null }
        )
    }

    if (showNewDeckSheet) {
        NewDeckDialog(
            store = store,
            onDismiss = { showNewDeckSheet = false },
            onCreated = { created ->
                newDeckName = created
                selectedDeckFilter = created
                showNewDeckSheet = false
            }
        )
    }

    deckToRename?.let { oldName ->
        RenameDeckDialog(
            store = store,
            oldName = oldName,
            onDismiss = { deckToRename = null },
            onRenamed = { renamed ->
                if (selectedDeckFilter == oldName) selectedDeckFilter = renamed
                if (newDeckName == oldName) newDeckName = renamed
                deckToRename = null
            }
        )
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text("Bilgi", fontWeight = FontWeight.Bold) },
            text = { Text(alertMessage ?: "") },
            confirmButton = {
                TextButton(onClick = { showAlert = false }) {
                    Text("Tamam")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordRowItem(
    card: Flashcard,
    index: Int,
    dueText: String,
    onSpeak: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalAppColors.current
    val bg = if (index % 2 == 0) Color.Transparent else colors.primaryText.copy(alpha = 0.035f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .combinedClickable(
                onClick = {},
                onDoubleClick = onEdit
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Artikel Badge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(44.dp)
                .background(card.artikel.badgeBackground, RoundedCornerShape(6.dp))
                .padding(vertical = 5.dp)
        ) {
            Text(
                text = card.artikel.rawValue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = card.artikel.color
            )
        }

        // Word, Meaning & Metadata Badges
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = card.word,
                fontSize = if (card.word.length > 18) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.primaryText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (card.meaning.isNotEmpty()) {
                Text(
                    text = card.meaning,
                    fontSize = 12.sp,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Compact Metadata Sub-row: Deck Tag, Stage Badge, Due Description
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(colors.secondaryText.copy(alpha = 0.12f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = colors.secondaryText,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = card.deckName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = card.stage.title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = card.stage.color,
                    modifier = Modifier
                        .background(card.stage.color.copy(alpha = 0.14f), CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )

                Text(
                    text = dueText,
                    fontSize = 10.sp,
                    color = colors.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Action Buttons: Speak, Edit, Delete
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onSpeak, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "Seslendir",
                    tint = colors.primaryText,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Kelimeyi Düzenle",
                    tint = colors.accentBlue,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Kelimeyi Sil",
                    tint = colors.vividRed.copy(alpha = 0.85f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

private fun dueDescription(card: Flashcard, nowMillis: Long): String {
    if (card.stage == CardStage.NEW) return "Sırada (Yeni)"
    if (card.dueDate <= nowMillis) return "Şimdi"
    val diffSec = (card.dueDate - nowMillis) / 1000.0
    return when {
        diffSec < 60 -> "${max(1, ceil(diffSec).toInt())} sn sonra"
        diffSec < 3600 -> "${max(1, (diffSec / 60.0).roundToInt())} dk sonra"
        diffSec < 86_400 -> "${max(1, (diffSec / 3600.0).roundToInt())} sa sonra"
        else -> "${max(1, (diffSec / 86_400.0).roundToInt())} gün sonra"
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.textBackground)
            .border(1.dp, colors.cardBorderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                fontSize = 12.sp,
                color = colors.secondaryText.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = colors.primaryText
            ),
            cursorBrush = SolidColor(colors.accentBlue),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeckFilterChip(
    title: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val colors = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isSelected) colors.accentBlue else colors.textBackground)
            .border(
                1.dp,
                if (isSelected) colors.accentBlue else colors.cardBorderSubtle,
                CircleShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.Folder else Icons.Default.FolderOpen,
            contentDescription = null,
            tint = if (isSelected) Color.White else colors.secondaryText,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) Color.White else colors.primaryText,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "$count",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.White else colors.secondaryText,
            modifier = Modifier
                .background(
                    if (isSelected) Color.White.copy(alpha = 0.22f)
                    else colors.secondaryText.copy(alpha = 0.15f),
                    CircleShape
                )
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

// MARK: - New Deck Creation Modal Dialog (1:1 Port of NewDeckSheet)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewDeckDialog(
    store: DeckStore,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var deckName by remember { mutableStateOf("") }

    val suggestedDecks = listOf(
        "A1 Kelimeler", "A2 Kelimeler", "B1 Kelimeler",
        "B2 Kelimeler", "C1 Kelimeler", "Günlük Konuşma",
        "İş Almancası", "Yiyecek & İçecek", "Seyahat"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.cardBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = colors.accentBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Yeni Deste Oluştur",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Kapat",
                            tint = colors.secondaryText
                        )
                    }
                }

                Text(
                    text = "Kelime haznenizi konulara veya seviyelere göre ayırmak için yeni bir deste adı girin:",
                    fontSize = 12.sp,
                    color = colors.secondaryText
                )

                OutlinedTextField(
                    value = deckName,
                    onValueChange = { deckName = it },
                    placeholder = { Text("Deste adı (örn: B1 Sınav, Mutfak, Akademik)", fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Hızlı Öneriler:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.secondaryText
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggestedDecks.forEach { suggestion ->
                            val isChosen = deckName == suggestion
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isChosen) colors.accentBlue.copy(alpha = 0.2f)
                                        else colors.secondaryText.copy(alpha = 0.1f)
                                    )
                                    .clickable { deckName = suggestion }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = if (isChosen) colors.accentBlue else colors.primaryText,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = suggestion,
                                    fontSize = 11.sp,
                                    color = if (isChosen) colors.accentBlue else colors.primaryText
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.cardBorderSubtle)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val created = store.createDeck(deckName)
                            if (created != null) {
                                onCreated(created)
                            }
                        },
                        enabled = deckName.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Deste Oluştur")
                    }
                }
            }
        }
    }
}

// MARK: - Rename Deck Dialog (1:1 Port of RenameDeckSheet)

@Composable
fun RenameDeckDialog(
    store: DeckStore,
    oldName: String,
    onDismiss: () -> Unit,
    onRenamed: (String) -> Unit
) {
    val colors = LocalAppColors.current
    var newName by remember(oldName) { mutableStateOf(oldName) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.cardBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Text(
                    text = "'$oldName' Destesinin Adını Değiştir",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryText
                )

                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("Yeni deste adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val cleaned = newName.trim()
                            if (cleaned.isNotEmpty()) {
                                store.renameDeck(oldName = oldName, newName = cleaned)
                                onRenamed(cleaned)
                            }
                        },
                        enabled = newName.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Kaydet")
                    }
                }
            }
        }
    }
}

// MARK: - Edit Word Modal Dialog (1:1 Port of EditWordSheet)

@Composable
fun EditWordDialog(
    store: DeckStore,
    card: Flashcard,
    onDismiss: () -> Unit
) {
    val colors = LocalAppColors.current
    var editArtikel by remember(card.id) { mutableStateOf(card.artikel) }
    var editWord by remember(card.id) { mutableStateOf(card.word) }
    var editMeaning by remember(card.id) { mutableStateOf(card.meaning) }
    var editDeckName by remember(card.id) { mutableStateOf(card.deckName) }
    var showDeckDropdown by remember { mutableStateOf(false) }
    var showNewDeckSheet by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = colors.controlBackground,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, colors.cardBorderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = colors.accentBlue
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kelimeyi Düzenle",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primaryText,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = "Kapat",
                            tint = colors.secondaryText
                        )
                    }
                }

                HorizontalDivider(color = colors.cardBorderSubtle)

                Text(
                    text = "Artikel:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Artikel.entries.forEach { art ->
                        val isSelected = editArtikel == art
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) art.color else colors.textBackground
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) art.color else colors.cardBorderSubtle,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { editArtikel = art }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = art.rawValue,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else colors.primaryText
                            )
                        }
                    }
                }

                Text(
                    text = "Almanca Kelime:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText
                )
                OutlinedTextField(
                    value = editWord,
                    onValueChange = { editWord = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Türkçe Anlamı / Açıklama:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText
                )
                OutlinedTextField(
                    value = editMeaning,
                    onValueChange = { editMeaning = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Deste / Kategori:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primaryText
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            color = colors.textBackground,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, colors.cardBorderSubtle),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDeckDropdown = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = colors.accentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = editDeckName,
                                    fontSize = 13.sp,
                                    color = colors.primaryText
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showDeckDropdown,
                            onDismissRequest = { showDeckDropdown = false }
                        ) {
                            store.allDeckNames.forEach { existingDeck ->
                                DropdownMenuItem(
                                    text = { Text(existingDeck) },
                                    onClick = {
                                        editDeckName = existingDeck
                                        showDeckDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedButton(onClick = { showNewDeckSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Yeni Deste", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(color = colors.cardBorderSubtle)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("İptal")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            store.createDeck(editDeckName)
                            store.updateCard(
                                id = card.id,
                                artikel = editArtikel,
                                word = editWord,
                                meaning = editMeaning,
                                deckName = editDeckName
                            )
                            onDismiss()
                        },
                        enabled = editWord.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentBlue,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Değişiklikleri Kaydet")
                    }
                }
            }
        }
    }

    if (showNewDeckSheet) {
        NewDeckDialog(
            store = store,
            onDismiss = { showNewDeckSheet = false },
            onCreated = { created ->
                editDeckName = created
                showNewDeckSheet = false
            }
        )
    }
}

// MARK: - Previews for Android Studio Design Mode

@Preview(name = "Kelime Listesi Satır Önizleme", showBackground = true, backgroundColor = 0xFF161618)
@Composable
private fun WordListRowPreview() {
    val sampleCards = listOf(
        Flashcard(artikel = com.artikelmeister.android.Artikel.DER, word = "Tisch", meaning = "Masa", deckName = "A1"),
        Flashcard(artikel = com.artikelmeister.android.Artikel.DIE, word = "Lampe", meaning = "Lamba", deckName = "A1"),
        Flashcard(artikel = com.artikelmeister.android.Artikel.DAS, word = "Buch", meaning = "Kitap", deckName = "A1")
    )
    ArtikelMeisterTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF161618))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sampleCards.forEach { card ->
                Surface(
                    color = Color(0xFF212124),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0x24FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Surface(
                            color = card.artikel.color.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = card.artikel.rawValue,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = card.artikel.color,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.word,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFF5F5F7)
                            )
                            if (card.meaning.isNotEmpty()) {
                                Text(
                                    text = card.meaning,
                                    fontSize = 12.sp,
                                    color = Color(0xFF98989F)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
