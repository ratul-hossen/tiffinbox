package com.ratulhossen.tiffinbox.ui.screens.chefside

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ratulhossen.tiffinbox.R
import com.ratulhossen.tiffinbox.data.SampleData
import com.ratulhossen.tiffinbox.data.firebase.FirestoreCatalogRepository
import com.ratulhossen.tiffinbox.data.firebase.FirestoreMealRepository
import com.ratulhossen.tiffinbox.data.model.MealUi
import com.ratulhossen.tiffinbox.ui.components.Base64Thumbnail
import com.ratulhossen.tiffinbox.ui.components.TiffinButton
import com.ratulhossen.tiffinbox.util.ImageCompressor
import kotlinx.coroutines.launch

/**
 * Chef's menu: add / edit / delete meals, add or change each meal's photo
 * (straight from the list, no need to re-create the meal), switch a meal
 * on/off for customers, and pick up to [FirestoreMealRepository.MAX_TODAY_SPECIALS]
 * "Today's Special" meals that Home features in its banner. Photos are
 * optional, but meals without one get a visible nudge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuManagerScreen(chefId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var meals by remember(chefId) { mutableStateOf<List<MealUi>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    // null = closed, a MealUi with blank id = adding, otherwise editing that meal.
    var editing by remember { mutableStateOf<MealUi?>(null) }
    var deleting by remember { mutableStateOf<MealUi?>(null) }
    var photoTargetId by remember { mutableStateOf<String?>(null) }
    var uploadingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(chefId) {
        meals = FirestoreCatalogRepository.getMealsForChef(chefId, includeUnavailable = true)
        loading = false
    }

    fun replace(updated: MealUi) {
        meals = meals.map { if (it.id == updated.id) updated else it }
    }

    fun toast(res: Int) = Toast.makeText(context, context.getString(res), Toast.LENGTH_SHORT).show()

    // One picker for the quick "add / change photo" button on each card.
    val quickPhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = meals.find { it.id == photoTargetId }
        photoTargetId = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        uploadingId = target.id
        scope.launch {
            val base64 = ImageCompressor.compressMealPhoto(context, uri)
            if (base64 == null) {
                toast(R.string.menu_photo_failed)
            } else {
                val updated = target.copy(photoBase64 = base64)
                if (FirestoreMealRepository.updateMeal(updated)) {
                    replace(updated)
                    toast(R.string.menu_photo_saved)
                } else {
                    toast(R.string.menu_save_failed)
                }
            }
            uploadingId = null
        }
    }

    val withoutPhoto = meals.count { it.photoBase64.isBlank() }
    val specialsToday = meals.count { it.isTodaySpecial }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Text(stringResource(R.string.menu_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = blankMeal(chefId) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.menu_manager_add_meal_content_description)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TipCard(
                    emoji = "⭐",
                    title = stringResource(R.string.menu_special_tip_title, specialsToday, FirestoreMealRepository.MAX_TODAY_SPECIALS),
                    body = stringResource(R.string.menu_special_tip_body)
                )
            }
            if (withoutPhoto > 0) {
                item {
                    TipCard(
                        emoji = "📷",
                        title = stringResource(R.string.menu_photo_tip_title, withoutPhoto),
                        body = stringResource(R.string.menu_photo_tip_body),
                        highlighted = true
                    )
                }
            }
            if (!loading && meals.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.menu_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(meals, key = { it.id }) { meal ->
                MenuMealCard(
                    meal = meal,
                    uploading = uploadingId == meal.id,
                    onPhotoClick = {
                        photoTargetId = meal.id
                        quickPhotoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onToggleAvailable = { on ->
                        replace(meal.copy(isAvailable = on))
                        scope.launch {
                            if (!FirestoreMealRepository.updateAvailability(meal.id, on)) {
                                replace(meal)
                                toast(R.string.menu_save_failed)
                            }
                        }
                    },
                    onToggleSpecial = {
                        val makeSpecial = !meal.isTodaySpecial
                        if (makeSpecial && specialsToday >= FirestoreMealRepository.MAX_TODAY_SPECIALS) {
                            toast(R.string.menu_special_limit)
                            return@MenuMealCard
                        }
                        replace(meal.copy(specialDate = if (makeSpecial) MealUi.todayKey() else ""))
                        scope.launch {
                            if (!FirestoreMealRepository.setTodaySpecial(meal.id, makeSpecial)) {
                                replace(meal)
                                toast(R.string.menu_save_failed)
                            }
                        }
                    },
                    onEdit = { editing = meal },
                    onDelete = { deleting = meal }
                )
            }
        }
    }

    editing?.let { initial ->
        MealEditorDialog(
            initial = initial,
            onDismiss = { editing = null },
            onSave = { meal ->
                scope.launch {
                    if (meal.id.isBlank()) {
                        val id = FirestoreMealRepository.addMeal(meal)
                        if (id != null) {
                            meals = meals + meal.copy(id = id)
                            editing = null
                        } else {
                            toast(R.string.menu_save_failed)
                        }
                    } else if (FirestoreMealRepository.updateMeal(meal)) {
                        replace(meal)
                        editing = null
                    } else {
                        toast(R.string.menu_save_failed)
                    }
                }
            }
        )
    }

    deleting?.let { meal ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.menu_delete_title)) },
            text = { Text(stringResource(R.string.menu_delete_message, meal.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        if (FirestoreMealRepository.deleteMeal(meal.id)) meals = meals.filter { it.id != meal.id }
                        else toast(R.string.menu_save_failed)
                    }
                }) { Text(stringResource(R.string.menu_delete_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.menu_manager_cancel_button)) }
            }
        )
    }
}

private fun blankMeal(chefId: String) = MealUi(
    id = "",
    chefId = chefId,
    name = "",
    emoji = "🍽️",
    description = "",
    price = 0,
    category = "Lunch",
    isVeg = false,
    allergens = emptyList(),
    dietaryTags = emptyList(),
    availableQuantity = 10,
    portionSize = "1 plate"
)

@Composable
private fun TipCard(emoji: String, title: String, body: String, highlighted: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MenuMealCard(
    meal: MealUi,
    uploading: Boolean,
    onPhotoClick: () -> Unit,
    onToggleAvailable: (Boolean) -> Unit,
    onToggleSpecial: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Photo in the same 4:3 frame customers see; tap to add / change.
                Box(
                    modifier = Modifier
                        .width(112.dp)
                        .aspectRatio(ImageCompressor.MEAL_ASPECT)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onPhotoClick)
                ) {
                    Base64Thumbnail(
                        base64 = meal.photoBase64,
                        emoji = meal.emoji,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(14.dp),
                        emojiFontSize = 34.sp
                    )
                    if (uploading) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp) }
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        ) {
                            Icon(
                                if (meal.photoBase64.isBlank()) Icons.Filled.AddAPhoto else Icons.Filled.PhotoCamera,
                                contentDescription = stringResource(R.string.menu_change_photo),
                                tint = Color.White,
                                modifier = Modifier.padding(5.dp).size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        meal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${meal.category} · " + stringResource(R.string.menu_manager_item_price_quantity, meal.price, meal.availableQuantity),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (meal.photoBase64.isBlank()) {
                        Text(
                            stringResource(R.string.menu_no_photo_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.clickable(onClick = onPhotoClick).padding(top = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = meal.isTodaySpecial,
                    onClick = onToggleSpecial,
                    leadingIcon = {
                        Icon(
                            if (meal.isTodaySpecial) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text(stringResource(R.string.menu_today_special)) }
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.menu_edit)) }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.menu_delete_confirm), tint = MaterialTheme.colorScheme.error)
                }
                Switch(checked = meal.isAvailable, onCheckedChange = onToggleAvailable)
            }
            if (!meal.isAvailable) {
                Text(
                    stringResource(R.string.menu_hidden_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Add (blank id) or edit a meal. The photo is compressed as soon as it's picked, so the preview is exactly what customers will see. */
@Composable
private fun MealEditorDialog(initial: MealUi, onDismiss: () -> Unit, onSave: (MealUi) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial.name) }
    var description by remember { mutableStateOf(initial.description) }
    var price by remember { mutableStateOf(if (initial.price > 0) initial.price.toString() else "") }
    var quantity by remember { mutableStateOf(initial.availableQuantity.toString()) }
    var category by remember { mutableStateOf(initial.category) }
    var isVeg by remember { mutableStateOf(initial.isVeg) }
    var photoBase64 by remember { mutableStateOf(initial.photoBase64) }
    var processing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        processing = true
        scope.launch {
            val result = ImageCompressor.compressMealPhoto(context, uri)
            if (result != null) photoBase64 = result else error = context.getString(R.string.menu_photo_failed)
            processing = false
        }
    }
    val pick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (initial.id.isBlank()) R.string.menu_manager_add_meal_content_description else R.string.menu_edit_title))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(ImageCompressor.MEAL_ASPECT)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !processing) { pick() }
                ) {
                    Base64Thumbnail(
                        base64 = photoBase64,
                        emoji = if (photoBase64.isBlank()) "📷" else initial.emoji,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        emojiFontSize = 40.sp
                    )
                    if (processing) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else if (photoBase64.isBlank()) {
                        Text(
                            stringResource(R.string.menu_photo_optional_hint),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                        )
                    }
                }
                Row {
                    TextButton(onClick = pick, enabled = !processing) {
                        Text(stringResource(if (photoBase64.isBlank()) R.string.menu_manager_select_photo_button else R.string.menu_change_photo))
                    }
                    if (photoBase64.isNotBlank()) {
                        TextButton(onClick = { photoBase64 = "" }, enabled = !processing) {
                            Text(stringResource(R.string.menu_remove_photo), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.menu_manager_add_meal_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.menu_description_label)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it.filter(Char::isDigit).take(5) },
                        label = { Text(stringResource(R.string.menu_manager_add_meal_price_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.filter(Char::isDigit).take(3) },
                        label = { Text(stringResource(R.string.menu_quantity_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.menu_manager_category_label), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SampleData.categories.forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.menu_veg_label), modifier = Modifier.weight(1f))
                    Switch(checked = isVeg, onCheckedChange = { isVeg = it })
                }
                if (error != null) {
                    Text(error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TiffinButton(
                enabled = !processing,
                onClick = {
                    val priceValue = price.toIntOrNull() ?: 0
                    if (name.isBlank() || priceValue <= 0) {
                        error = context.getString(R.string.menu_name_price_required)
                        return@TiffinButton
                    }
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            description = description.trim(),
                            price = priceValue,
                            availableQuantity = quantity.toIntOrNull() ?: 0,
                            category = category,
                            isVeg = isVeg,
                            photoBase64 = photoBase64
                        )
                    )
                }
            ) { Text(stringResource(if (initial.id.isBlank()) R.string.menu_manager_add_button else R.string.edit_profile_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.menu_manager_cancel_button)) }
        }
    )
}
