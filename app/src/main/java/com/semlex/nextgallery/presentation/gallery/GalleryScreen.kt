package com.semlex.nextgallery.presentation.gallery

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.semlex.nextgallery.R
import com.semlex.nextgallery.data.auth.CredentialsDataStore
import com.semlex.nextgallery.data.media.NextcloudMediaRepository
import com.semlex.nextgallery.domain.media.MediaItem
import com.semlex.nextgallery.domain.media.TrashItem
import com.semlex.nextgallery.ui.theme.NextGalleryTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun GalleryRoute(
    credentialsDataStore: CredentialsDataStore,
    mediaRepository: NextcloudMediaRepository,
    onLoggedOut: () -> Unit,
    viewModel: GalleryViewModel = viewModel(
        factory = GalleryViewModelFactory(
            credentialsDataStore = credentialsDataStore,
            mediaRepository = mediaRepository
        )
    )
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    GalleryScreen(
        uiState = uiState,
        onRetryClick = viewModel::refresh,
        onLogoutClick = { viewModel.logout(onLoggedOut) },
        onMoveToTrashClick = viewModel::deleteImages,
        onUploadClick = viewModel::uploadImage,
        onTrashTabSelected = viewModel::loadTrash,
        onRestoreTrashClick = viewModel::restoreTrashItems,
        onDeleteTrashClick = viewModel::deleteTrashItems,
        onEmptyTrashClick = viewModel::emptyTrash
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun GalleryScreen(
    uiState: GalleryUiState,
    onRetryClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onMoveToTrashClick: (List<MediaItem>) -> Unit,
    onUploadClick: (android.net.Uri) -> Unit,
    onTrashTabSelected: () -> Unit,
    onRestoreTrashClick: (List<TrashItem>) -> Unit,
    onDeleteTrashClick: (List<TrashItem>) -> Unit,
    onEmptyTrashClick: () -> Unit
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedTrashIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedTab by remember { mutableStateOf(GalleryTab.Photos) }
    var sortOrder by remember { mutableStateOf(PhotoSortOrder.NewestFirst) }
    var isSortMenuOpen by remember { mutableStateOf(false) }
    var scrollAnchorPhotoId by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val sortedItems = remember(uiState.items, sortOrder) {
        uiState.items.sortedWith(sortOrder.comparator)
    }
    val groupedItems = remember(sortedItems, sortOrder) {
        sortedItems.toDateSections(sortOrder)
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(onUploadClick) }
    )

    LaunchedEffect(uiState.items) {
        val availableIds = uiState.items.mapTo(mutableSetOf()) { it.id }
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in availableIds }
    }

    LaunchedEffect(uiState.trashItems) {
        val availableIds = uiState.trashItems.mapTo(mutableSetOf()) { it.id }
        selectedTrashIds = selectedTrashIds.filterTo(mutableSetOf()) { it in availableIds }
    }

    LaunchedEffect(sortOrder, groupedItems) {
        val anchorId = scrollAnchorPhotoId ?: return@LaunchedEffect
        val anchorIndex = groupedItems.indexOfFirst {
            it is GalleryGridItem.Photo && it.mediaItem.id == anchorId
        }

        if (anchorIndex >= 0) {
            gridState.scrollToItem(anchorIndex)
        }
        scrollAnchorPhotoId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Галерея",
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = if (uiState.items.isEmpty()) {
                            "Фото из Nextcloud появятся здесь."
                        } else {
                            "${uiState.items.size} фото из Nextcloud"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { uriHandler.openUri(GITHUB_URL) }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_github),
                            contentDescription = "GitHub",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    TextButton(
                        onClick = onLogoutClick,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text("Выйти")
                    }
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (selectedTab == GalleryTab.Photos && selectedIds.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            val selectedItems = uiState.items.filter { it.id in selectedIds }
                            selectedIds = emptySet()
                            onMoveToTrashClick(selectedItems)
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(
                            text = "В корзину",
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (selectedTab == GalleryTab.Photos) {
                    Box {
                        TextButton(
                            onClick = { isSortMenuOpen = true },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = "Сортировка",
                                maxLines = 1
                            )
                        }

                        DropdownMenu(
                            expanded = isSortMenuOpen,
                            onDismissRequest = { isSortMenuOpen = false }
                        ) {
                            PhotoSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.title) },
                                    onClick = {
                                        scrollAnchorPhotoId = gridState.firstVisiblePhotoId(groupedItems)
                                        sortOrder = order
                                        isSortMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !uiState.isUploading
                    ) {
                        Text(
                            text = if (uiState.isUploading) "..." else "+",
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                uiState.isLoading && uiState.items.isEmpty() -> LoadingState()
                uiState.errorMessage != null -> ErrorState(
                    message = uiState.errorMessage,
                    onRetryClick = onRetryClick
                )
                selectedTab == GalleryTab.Photos -> when {
                    sortedItems.isEmpty() -> EmptyState()
                    else -> PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = onRetryClick,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        PhotoGrid(
                            items = groupedItems,
                            gridState = gridState,
                            authHeader = uiState.authHeader,
                            selectedIds = selectedIds,
                            onItemClick = { item ->
                                if (selectedIds.isNotEmpty()) {
                                    selectedIds = selectedIds.toggle(item.id)
                                } else {
                                    selectedIndex = sortedItems.indexOfFirst { it.id == item.id }
                                }
                            },
                            onItemLongClick = { item ->
                                selectedIds = selectedIds.toggle(item.id)
                            }
                        )
                    }
                }
                selectedTab == GalleryTab.Trash -> TrashContent(
                    uiState = uiState,
                    selectedIds = selectedTrashIds,
                    onToggleSelection = { item ->
                        selectedTrashIds = selectedTrashIds.toggle(item.id)
                    },
                    onRestoreClick = {
                        val selectedItems = uiState.trashItems.filter { it.id in selectedTrashIds }
                        selectedTrashIds = emptySet()
                        onRestoreTrashClick(selectedItems)
                    },
                    onDeleteClick = {
                        val selectedItems = uiState.trashItems.filter { it.id in selectedTrashIds }
                        selectedTrashIds = emptySet()
                        onDeleteTrashClick(selectedItems)
                    },
                    onEmptyClick = onEmptyTrashClick
                )
                else -> MemoriesSectionPlaceholder(selectedTab)
            }
        }

        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            GalleryTab.entries.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab,
                    onClick = {
                        selectedTab = tab
                        selectedIds = emptySet()
                        selectedTrashIds = emptySet()
                        if (tab == GalleryTab.Trash) {
                            onTrashTabSelected()
                        }
                    },
                    icon = { Text(tab.icon) },
                    label = { Text(tab.title, maxLines = 1) }
                )
            }
        }
    }

    selectedIndex?.let { index ->
        PhotoViewerDialog(
            items = sortedItems,
            initialPage = index,
            authHeader = uiState.authHeader,
            onMoveToTrashClick = { item ->
                selectedIndex = null
                onMoveToTrashClick(listOf(item))
            },
            onDismissRequest = { selectedIndex = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoGrid(
    items: List<GalleryGridItem>,
    gridState: LazyGridState,
    authHeader: String?,
    selectedIds: Set<String>,
    onItemClick: (MediaItem) -> Unit,
    onItemLongClick: (MediaItem) -> Unit
) {
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 112.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp, end = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = items,
                key = { it.key },
                span = { item ->
                    if (item is GalleryGridItem.Header) {
                        GridItemSpan(maxLineSpan)
                    } else {
                        GridItemSpan(1)
                    }
                }
            ) { item ->
                when (item) {
                    is GalleryGridItem.Header -> DateHeader(item)
                    is GalleryGridItem.Photo -> PhotoTile(
                        item = item.mediaItem,
                        authHeader = authHeader,
                        isSelected = item.mediaItem.id in selectedIds,
                        selectionMode = selectedIds.isNotEmpty(),
                        onClick = { onItemClick(item.mediaItem) },
                        onLongClick = { onItemLongClick(item.mediaItem) }
                    )
                }
            }
        }

        FastScrollBar(
            itemCount = items.size,
            gridState = gridState,
            onScrollToIndex = { index ->
                scope.launch {
                    gridState.scrollToItem(index)
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun FastScrollBar(
    itemCount: Int,
    gridState: LazyGridState,
    onScrollToIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (itemCount <= FAST_SCROLL_MIN_ITEMS) return

    val visibleItems = gridState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
    val maxFirstIndex = (itemCount - visibleItems).coerceAtLeast(1)
    val progress = (gridState.firstVisibleItemIndex.toFloat() / maxFirstIndex).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(220.dp)
            .padding(end = 2.dp)
            .pointerInput(itemCount, maxFirstIndex) {
                detectDragGestures { change, _ ->
                    val y = change.position.y.coerceIn(0f, size.height.toFloat())
                    val targetIndex = ((y / size.height) * maxFirstIndex)
                        .toInt()
                        .coerceIn(0, itemCount - 1)
                    onScrollToIndex(targetIndex)
                    change.consume()
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 220.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Box(
            modifier = Modifier
                .padding(top = ((220 - 42) * progress).dp)
                .size(width = 5.dp, height = 42.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

private fun LazyGridState.firstVisiblePhotoId(items: List<GalleryGridItem>): String? {
    for (index in firstVisibleItemIndex until items.size) {
        val item = items[index]
        if (item is GalleryGridItem.Photo) {
            return item.mediaItem.id
        }
    }
    return null
}

@Composable
private fun DateHeader(item: GalleryGridItem.Header) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = item.title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "${item.count} ${item.count.photoWord()} с этого дня",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotoTile(
    item: MediaItem,
    authHeader: String?,
    isSelected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.downloadUrl)
                .apply {
                    authHeader?.let { addHeader("Authorization", it) }
                }
                .memoryCacheKey(item.cacheKey)
                .diskCacheKey(item.cacheKey)
                .size(384)
                .crossfade(true)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (selectionMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isSelected) Color(0x66000000) else Color(0x22000000))
            )
            Checkbox(
                checked = isSelected,
                onCheckedChange = null,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            )
        }
    }
}

@Composable
private fun PhotoViewerDialog(
    items: List<MediaItem>,
    initialPage: Int,
    authHeader: String?,
    onMoveToTrashClick: (MediaItem) -> Unit,
    onDismissRequest: () -> Unit
) {
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { items.size }
    )
    var currentScale by remember { mutableStateOf(1f) }

    LaunchedEffect(pagerState.currentPage) {
        currentScale = 1f
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = currentScale <= 1.01f,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                ZoomablePhoto(
                    item = items[page],
                    authHeader = authHeader,
                    onScaleChanged = { scale ->
                        if (page == pagerState.currentPage) {
                            currentScale = scale
                        }
                    }
                )
            }

            Text(
                text = "${pagerState.currentPage + 1} / ${items.size}",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 14.dp)
            )

            Button(
                onClick = onDismissRequest,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 8.dp, end = 12.dp)
            ) {
                Text("Закрыть")
            }

            if (currentScale <= 1.01f) {
                Button(
                    onClick = {
                        items.getOrNull(pagerState.currentPage)?.let(onMoveToTrashClick)
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 18.dp)
                ) {
                    Text("В корзину")
                }
            }
        }
    }
}

@Composable
private fun TrashContent(
    uiState: GalleryUiState,
    selectedIds: Set<String>,
    onToggleSelection: (TrashItem) -> Unit,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEmptyClick: () -> Unit
) {
    when {
        uiState.isTrashLoading -> LoadingState()
        uiState.trashItems.isEmpty() -> EmptyTrashState()
        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onRestoreClick,
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Восстановить")
                }
                TextButton(
                    onClick = onDeleteClick,
                    enabled = selectedIds.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }

            TextButton(
                onClick = onEmptyClick,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Очистить корзину")
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = uiState.trashItems,
                    key = { it.id }
                ) { item ->
                    TrashTile(
                        item = item,
                        isSelected = item.id in selectedIds,
                        onClick = { onToggleSelection(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashTile(
    item: TrashItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() }
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = item.originalLocation ?: "Исходный путь неизвестен",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun EmptyTrashState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Корзина пуста",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun MemoriesSectionPlaceholder(tab: GalleryTab) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = tab.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "Подключу через Memories API следующим шагом.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ZoomablePhoto(
    item: MediaItem,
    authHeader: String?,
    onScaleChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scale = remember(item.id) { Animatable(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(item.id) {
        scale.snapTo(1f)
        offset = Offset.Zero
        onScaleChanged(1f)
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(item.downloadUrl)
                .apply {
                    authHeader?.let { addHeader("Authorization", it) }
                }
                .memoryCacheKey(item.cacheKey)
                .diskCacheKey(item.cacheKey)
                .crossfade(true)
                .build(),
        contentDescription = item.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(item.id) {
                var lastTapTime = 0L

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPosition = down.position
                    var isTransforming = false
                    var moved = false
                    var upTime = down.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.count { it.pressed }

                        if (pressedPointers > 1) {
                            isTransforming = true

                            val nextScale = (scale.value * event.calculateZoom()).coerceIn(1f, 5f)
                            scope.launch {
                                scale.snapTo(nextScale)
                                onScaleChanged(nextScale)
                            }
                            offset = if (nextScale > 1f) {
                                offset + event.calculatePan()
                            } else {
                                Offset.Zero
                            }

                            event.changes
                                .filter { it.positionChanged() }
                                .forEach { it.consume() }
                        } else if (scale.value > 1f) {
                            val activePointer = event.changes.firstOrNull { it.pressed }
                            if (activePointer != null) {
                                moved = true
                                offset += activePointer.positionChange()
                                activePointer.consume()
                            }
                        } else {
                            val activePointer = event.changes.firstOrNull { it.pressed }
                            if (activePointer != null && (activePointer.position - downPosition).getDistance() > DOUBLE_TAP_SLOP_PX) {
                                moved = true
                            }
                        }

                        if (event.changes.all { !it.pressed }) {
                            upTime = event.changes.maxOf { it.uptimeMillis }
                            break
                        }
                    }

                    if (!isTransforming && !moved) {
                        if (upTime - lastTapTime <= DOUBLE_TAP_TIMEOUT_MS) {
                            if (scale.value > 1f) {
                                scope.launch {
                                    scale.animateTo(
                                        targetValue = 1f,
                                        animationSpec = tween(durationMillis = DOUBLE_TAP_ANIMATION_MS)
                                    )
                                    onScaleChanged(1f)
                                }
                                offset = Offset.Zero
                            } else {
                                scope.launch {
                                    scale.animateTo(
                                        targetValue = 2.5f,
                                        animationSpec = tween(durationMillis = DOUBLE_TAP_ANIMATION_MS)
                                    )
                                    onScaleChanged(2.5f)
                                }
                                offset = Offset.Zero
                            }
                            lastTapTime = 0L
                        } else {
                            lastTapTime = upTime
                        }
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offset.x
                translationY = offset.y
            }
    )
}

private const val DOUBLE_TAP_TIMEOUT_MS = 300L
private const val DOUBLE_TAP_ANIMATION_MS = 220
private const val DOUBLE_TAP_SLOP_PX = 24f
private const val FAST_SCROLL_MIN_ITEMS = 40
private const val GITHUB_URL = "https://github.com/Semleks"

private enum class GalleryTab(val title: String, val icon: String) {
    Photos("Фото", "Ф"),
    Albums("Альбомы", "А"),
    Favorites("Избранное", "★"),
    People("Люди", "Л"),
    Archive("Архив", "З"),
    Map("Карта", "К"),
    Places("Места", "М"),
    Tags("Метки", "#"),
    Trash("Корзина", "×")
}

private enum class PhotoSortOrder(
    val title: String,
    val comparator: Comparator<MediaItem>
) {
    NewestFirst(
        title = "Сначала новые",
        comparator = compareByDescending<MediaItem> { it.sortTime }
            .thenBy { it.name.lowercase() }
    ),
    OldestFirst(
        title = "Сначала старые",
        comparator = compareBy<MediaItem> { it.sortTime }
            .thenBy { it.name.lowercase() }
    )
}

private sealed interface GalleryGridItem {
    val key: String

    data class Header(
        val title: String,
        val count: Int
    ) : GalleryGridItem {
        override val key: String = "header_$title"
    }

    data class Photo(
        val mediaItem: MediaItem
    ) : GalleryGridItem {
        override val key: String = mediaItem.id
    }
}

private fun List<MediaItem>.toDateSections(sortOrder: PhotoSortOrder): List<GalleryGridItem> {
    return groupBy { it.lastModified.toDateTitle() }
        .toList()
        .sortedWith { first, second ->
            val firstDate = first.second.firstOrNull()?.sortTime ?: 0L
            val secondDate = second.second.firstOrNull()?.sortTime ?: 0L

            if (sortOrder == PhotoSortOrder.NewestFirst) {
                secondDate.compareTo(firstDate)
            } else {
                firstDate.compareTo(secondDate)
            }
        }
        .flatMap { (title, items) ->
            listOf(
                GalleryGridItem.Header(
                    title = title,
                    count = items.size
                )
            ) + items.map { item ->
                GalleryGridItem.Photo(
                    mediaItem = item
                )
            }
        }
}

private fun String?.toDateTitle(): String {
    if (this.isNullOrBlank()) return "Дата неизвестна"

    return runCatching {
        val sourceFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        val outputFormat = SimpleDateFormat("d MMMM, yyyy 'год'", Locale.forLanguageTag("ru"))
        outputFormat.format(requireNotNull(sourceFormat.parse(this)))
    }.getOrDefault("Дата неизвестна")
}

private val MediaItem.sortTime: Long
    get() = lastModified.toEpochMillis()

private fun String?.toEpochMillis(): Long {
    if (this.isNullOrBlank()) return 0L

    return runCatching {
        val sourceFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        requireNotNull(sourceFormat.parse(this)).time
    }.getOrDefault(0L)
}

private fun Int.photoWord(): String {
    val lastTwoDigits = this % 100
    val lastDigit = this % 10

    return when {
        lastTwoDigits in 11..14 -> "фото"
        lastDigit == 1 -> "фотка"
        lastDigit in 2..4 -> "фотки"
        else -> "фоток"
    }
}

private fun Set<String>.toggle(id: String): Set<String> {
    return if (id in this) {
        this - id
    } else {
        this + id
    }
}

private val MediaItem.cacheKey: String
    get() = "$downloadUrl|${sizeBytes ?: 0}|${lastModified.orEmpty()}"

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(30.dp),
            strokeWidth = 3.dp
        )
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.secondary,
            style = MaterialTheme.typography.bodyLarge
        )
        Button(onClick = onRetryClick) {
            Text("Повторить")
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Изображения не найдены",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private class GalleryViewModelFactory(
    private val credentialsDataStore: CredentialsDataStore,
    private val mediaRepository: NextcloudMediaRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return GalleryViewModel(
            credentialsDataStore = credentialsDataStore,
            mediaRepository = mediaRepository
        ) as T
    }
}

@Preview
@Composable
private fun GalleryScreenPreview() {
    NextGalleryTheme {
        GalleryScreen(
            uiState = GalleryUiState(
                isLoading = false,
                items = emptyList()
            ),
            onRetryClick = {},
            onLogoutClick = {},
            onMoveToTrashClick = {},
            onUploadClick = {},
            onTrashTabSelected = {},
            onRestoreTrashClick = {},
            onDeleteTrashClick = {},
            onEmptyTrashClick = {}
        )
    }
}
