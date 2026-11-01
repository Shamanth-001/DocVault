package com.personal.docvault.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF0E1420)
val Panel = Color(0xFF172030)
val PanelHigh = Color(0xFF212C40)
val Gold = Color(0xFFF4B860)
val Mist = Color(0xFF93A1B8)
val Coral = Color(0xFFF2726F)
val Mint = Color(0xFF5FD4A8)

private val colors = darkColorScheme(
    primary = Gold,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3A2F1C),
    onPrimaryContainer = Gold,
    secondary = Mist,
    background = Ink,
    onBackground = Color(0xFFEDF1F7),
    surface = Panel,
    onSurface = Color(0xFFEDF1F7),
    surfaceVariant = PanelHigh,
    onSurfaceVariant = Mist,
    surfaceContainerHigh = PanelHigh,
    outline = Color(0xFF3A475E),
    error = Coral,
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun VaultTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = colors, typography = type, shapes = shapes, content = content)

data class Category(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val numberLabel: String = "Number",
    val hasExpiry: Boolean = false,
    val extraFields: List<String> = emptyList(),
)

val categories = listOf(
    Category("aadhaar", "Aadhaar card", Icons.Filled.Fingerprint, Color(0xFFF4B860), "Aadhaar number"),
    Category("pan", "PAN card", Icons.Filled.CreditCard, Color(0xFF7FB6FF), "PAN number"),
    Category("passport", "Passport", Icons.Filled.Flight, Color(0xFF6FD3E8), "Passport number", true),
    Category("licence", "Driving licence", Icons.Filled.TwoWheeler, Color(0xFFFF9F7A), "Licence number", true),
    Category("voter", "Voter ID", Icons.Filled.HowToVote, Color(0xFFB79CFF), "EPIC number"),
    Category("vehicle", "Vehicle RC & insurance", Icons.Filled.DirectionsCar, Color(0xFF8FD694), "Registration number", true),
    Category("bank", "Bank account", Icons.Filled.AccountBalance, Color(0xFF5FD4A8), "Account number", false, listOf("Bank", "IFSC", "Branch")),
    Category("card", "Debit or credit card", Icons.Filled.CreditCard, Color(0xFFFF8FB1), "Card number", true, listOf("Bank")),
    Category("insurance", "Insurance", Icons.Filled.HealthAndSafety, Color(0xFF7FE0C3), "Policy number", true, listOf("Provider")),
    Category("education", "Education", Icons.Filled.School, Color(0xFFFFD36E), "Roll or register number"),
    Category("medical", "Medical", Icons.Filled.MedicalServices, Color(0xFFF2726F), "Record number"),
    Category("property", "Property", Icons.Filled.Home, Color(0xFFC9B38C), "Document number"),
    Category("bills", "Bills and receipts", Icons.Filled.Receipt, Color(0xFFE3C567), "Bill number", true),
    Category("id", "Other ID", Icons.Filled.Badge, Color(0xFF9FB4D4), "ID number", true),
    Category("other", "Other", Icons.Filled.Description, Color(0xFF93A1B8)),
)

fun categoryOf(key: String) = categories.firstOrNull { it.key == key } ?: categories.last()
