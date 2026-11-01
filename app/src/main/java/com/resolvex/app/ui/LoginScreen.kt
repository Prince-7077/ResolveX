package com.resolvex.app.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.resolvex.app.R
import com.resolvex.app.data.AuthResult
import com.resolvex.app.data.SessionManager
import com.resolvex.app.data.UserRepository
import com.resolvex.app.data.UserRole
import com.resolvex.app.data.UserSession
import com.resolvex.app.ui.theme.ElectricBlue
import com.resolvex.app.ui.theme.ElectricCyan
import com.resolvex.app.ui.theme.NavyCard
import com.resolvex.app.ui.theme.NavyDark
import com.resolvex.app.ui.theme.NavyDeep
import com.resolvex.app.ui.theme.NeonPurple
import com.resolvex.app.ui.theme.SoftCyan
import com.resolvex.app.ui.theme.TextMuted
import com.resolvex.app.ui.theme.TextPrimary
import com.resolvex.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

enum class LoginTab {
    CITIZEN,
    ADMIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
    onCitizenLoginSuccess: () -> Unit = {},
    onAdminLoginSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val sessionManager = remember { SessionManager.getInstance(context) }
    val userRepo = remember { UserRepository.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    // Active Tab: Citizen vs Admin
    var selectedTab by remember { mutableStateOf(LoginTab.CITIZEN) }

    // Citizen Form States
    var isCitizenRegisterMode by remember { mutableStateOf(true) }
    var fullName by remember { mutableStateOf("") }
    var mobileNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isOccupationDropdownExpanded by remember { mutableStateOf(false) }

    // Citizen Errors
    var fullNameError by remember { mutableStateOf<String?>(null) }
    var mobileError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var occupationError by remember { mutableStateOf<String?>(null) }
    var generalCitizenError by remember { mutableStateOf<String?>(null) }

    // Admin Form States
    var adminEmail by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var adminAccessKey by remember { mutableStateOf("") }
    var isAdminPasswordVisible by remember { mutableStateOf(false) }
    var adminError by remember { mutableStateOf<String?>(null) }

    // Forgot Password State
    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }
    var resetError by remember { mutableStateOf<String?>(null) }

    // Submitting status
    var isSubmitting by remember { mutableStateOf(false) }

    val occupationOptions = listOf(
        "Student",
        "General Citizen",
        "Government Employee",
        "Private Employee",
        "Teacher",
        "Business Owner",
        "Homemaker",
        "Other"
    )

    fun validateCitizenForm(): Boolean {
        var isValid = true
        generalCitizenError = null

        if (isCitizenRegisterMode) {
            if (fullName.trim().isEmpty()) {
                fullNameError = "Please enter your full name"
                isValid = false
            } else if (fullName.trim().length < 2) {
                fullNameError = "Name must be at least 2 characters"
                isValid = false
            } else {
                fullNameError = null
            }

            val cleanMobile = mobileNumber.trim().replace(" ", "").replace("-", "")
            if (cleanMobile.isEmpty()) {
                mobileError = "Please enter your mobile number"
                isValid = false
            } else if (cleanMobile.length != 10 || !cleanMobile.all { it.isDigit() }) {
                mobileError = "Enter a valid 10-digit mobile number"
                isValid = false
            } else {
                mobileError = null
            }

            if (occupation.trim().isEmpty()) {
                occupationError = "Please select your occupation"
                isValid = false
            } else {
                occupationError = null
            }
        }

        val emailPattern = android.util.Patterns.EMAIL_ADDRESS
        if (email.trim().isEmpty()) {
            emailError = "Please enter your email address"
            isValid = false
        } else if (!emailPattern.matcher(email.trim()).matches()) {
            emailError = "Enter a valid email address (e.g. name@example.com)"
            isValid = false
        } else {
            emailError = null
        }

        if (password.isEmpty()) {
            passwordError = "Password cannot be empty"
            isValid = false
        } else if (password.length < 6) {
            passwordError = "Password must be at least 6 characters"
            isValid = false
        } else {
            passwordError = null
        }

        return isValid
    }

    val gradient = Brush.verticalGradient(
        colors = listOf(
            NavyDeep,
            NavyDark,
            Color(0xFF040817)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                TextButton(
                    onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("onboarding")
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text("← Onboarding", color = SoftCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Brand Logo
            Image(
                painter = painterResource(id = R.drawable.resolvex_logo),
                contentDescription = "ResolveX Logo",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )

            Spacer(modifier = Modifier.height(8.dp))

            // App Title
            Text(
                text = "ResolveX",
                color = TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )

            Text(
                text = "Your Voice. Real Change.",
                color = ElectricCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Evaluation Chips
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0C152B))
                    .border(0.5.dp, Color(0xFF1E2E4E), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "Quick Evaluation Fill (Tap to Test):",
                    color = SoftCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoleQuickChip("Citizen") {
                        selectedTab = LoginTab.CITIZEN
                        isCitizenRegisterMode = false
                        email = "citizen@resolvex.org"
                        password = "Citizen@2026"
                    }
                    RoleQuickChip("Roads Officer") {
                        selectedTab = LoginTab.ADMIN
                        adminEmail = "officer.roads@resolvex.gov.in"
                        adminPassword = "Officer@2026"
                        adminAccessKey = "Roads & Infrastructure"
                    }
                    RoleQuickChip("Water Officer") {
                        selectedTab = LoginTab.ADMIN
                        adminEmail = "officer.water@resolvex.gov.in"
                        adminPassword = "Officer@2026"
                        adminAccessKey = "Water Supply & Leakage"
                    }
                    RoleQuickChip("Municipal Admin") {
                        selectedTab = LoginTab.ADMIN
                        adminEmail = "admin@resolvex.gov.in"
                        adminPassword = "Admin@2026"
                        adminAccessKey = "Municipal Affairs"
                    }
                    RoleQuickChip("Super Admin") {
                        selectedTab = LoginTab.ADMIN
                        adminEmail = "superadmin@resolvex.gov.in"
                        adminPassword = "SuperAdmin@2026"
                        adminAccessKey = "Apex Governance Council"
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Role Switcher Tab Row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E2E4E), RoundedCornerShape(12.dp))
                    .padding(3.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(9.dp))
                            .then(
                                if (selectedTab == LoginTab.CITIZEN)
                                    Modifier.background(Brush.horizontalGradient(listOf(ElectricCyan, ElectricBlue)))
                                else
                                    Modifier.background(Color.Transparent)
                            )
                            .clickable {
                                selectedTab = LoginTab.CITIZEN
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Citizen Portal",
                            color = if (selectedTab == LoginTab.CITIZEN) NavyDeep else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(9.dp))
                            .then(
                                if (selectedTab == LoginTab.ADMIN)
                                    Modifier.background(Brush.horizontalGradient(listOf(NeonPurple, ElectricBlue)))
                                else
                                    Modifier.background(Color.Transparent)
                            )
                            .clickable {
                                selectedTab = LoginTab.ADMIN
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Official / Admin",
                            color = if (selectedTab == LoginTab.ADMIN) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --------------------- CITIZEN LOGIN / REGISTER ---------------------
            if (selectedTab == LoginTab.CITIZEN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCitizenRegisterMode) "Already registered?" else "New to ResolveX?",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                    TextButton(
                        onClick = {
                            isCitizenRegisterMode = !isCitizenRegisterMode
                            fullNameError = null
                            mobileError = null
                            emailError = null
                            passwordError = null
                            occupationError = null
                            generalCitizenError = null
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = if (isCitizenRegisterMode) "Sign In" else "Create Account",
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (isCitizenRegisterMode) {
                    // Full Name
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            if (fullNameError != null) fullNameError = null
                        },
                        label = { Text("Full Name *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        modifier = Modifier.fillMaxWidth(),
                        isError = fullNameError != null,
                        shape = RoundedCornerShape(14.dp),
                        colors = customFieldColors()
                    )
                    if (fullNameError != null) {
                        Text(
                            text = fullNameError ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Mobile Number
                    OutlinedTextField(
                        value = mobileNumber,
                        onValueChange = {
                            if (it.length <= 10) {
                                mobileNumber = it
                                if (mobileError != null) mobileError = null
                            }
                        },
                        label = { Text("Mobile Number (10 digits) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth(),
                        isError = mobileError != null,
                        shape = RoundedCornerShape(14.dp),
                        colors = customFieldColors()
                    )
                    if (mobileError != null) {
                        Text(
                            text = mobileError ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Email Address
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        if (emailError != null) emailError = null
                        generalCitizenError = null
                    },
                    label = { Text("Email Address *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    isError = emailError != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = customFieldColors()
                )
                if (emailError != null) {
                    Text(
                        text = emailError ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Password with Show/Hide Toggle
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        if (passwordError != null) passwordError = null
                        generalCitizenError = null
                    },
                    label = { Text("Password *") },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Text(
                                text = if (isPasswordVisible) "HIDE" else "SHOW",
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    isError = passwordError != null,
                    shape = RoundedCornerShape(14.dp),
                    colors = customFieldColors()
                )
                if (passwordError != null) {
                    Text(
                        text = passwordError ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp)
                    )
                }

                if (!isCitizenRegisterMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                resetEmail = email
                                resetNewPassword = ""
                                resetError = null
                                showForgotPasswordDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Forgot Password?", color = SoftCyan, fontSize = 11.sp)
                        }
                    }
                }

                if (isCitizenRegisterMode) {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Occupation Dropdown
                    ExposedDropdownMenuBox(
                        expanded = isOccupationDropdownExpanded,
                        onExpandedChange = { isOccupationDropdownExpanded = !isOccupationDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = occupation,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Occupation *") },
                            placeholder = { Text("Select your occupation", color = TextMuted) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isOccupationDropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            isError = occupationError != null,
                            shape = RoundedCornerShape(14.dp),
                            colors = customFieldColors()
                        )

                        ExposedDropdownMenu(
                            expanded = isOccupationDropdownExpanded,
                            onDismissRequest = { isOccupationDropdownExpanded = false },
                            modifier = Modifier.background(NavyDark)
                        ) {
                            occupationOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = option, color = TextPrimary) },
                                    onClick = {
                                        occupation = option
                                        isOccupationDropdownExpanded = false
                                        if (occupationError != null) occupationError = null
                                    }
                                )
                            }
                        }
                    }
                    if (occupationError != null) {
                        Text(
                            text = occupationError ?: "",
                            color = Color(0xFFFF5252),
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (generalCitizenError != null) {
                    Text(
                        text = generalCitizenError ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    )
                }

                // Submit Button for Citizen
                Button(
                    onClick = {
                        if (!isSubmitting && validateCitizenForm()) {
                            isSubmitting = true
                            coroutineScope.launch {
                                delay(300)
                                if (isCitizenRegisterMode) {
                                    val registerResult = userRepo.registerCitizen(
                                        fullName = fullName.trim(),
                                        email = email.trim(),
                                        mobile = mobileNumber.trim(),
                                        occupation = occupation.ifBlank { "General Citizen" },
                                        plainPassword = password
                                    )
                                    isSubmitting = false
                                    registerResult.onSuccess { account ->
                                        val session = UserSession(
                                            userId = account.id,
                                            fullName = account.fullName,
                                            email = account.email,
                                            mobile = account.mobile,
                                            occupation = account.occupation,
                                            role = UserRole.CITIZEN,
                                            department = null
                                        )
                                        sessionManager.saveSession(session)
                                        onCitizenLoginSuccess()
                                        navController.navigate("citizen_dashboard") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    }.onFailure { err ->
                                        generalCitizenError = err.message ?: "Registration failed. Please try again."
                                    }
                                } else {
                                    val authResult = userRepo.authenticate(email.trim(), password)
                                    isSubmitting = false
                                    when (authResult) {
                                        is AuthResult.Success -> {
                                            val account = authResult.user
                                            val session = UserSession(
                                                userId = account.id,
                                                fullName = account.fullName,
                                                email = account.email,
                                                mobile = account.mobile,
                                                occupation = account.occupation,
                                                role = account.role,
                                                department = account.department
                                            )
                                            sessionManager.saveSession(session)
                                            onCitizenLoginSuccess()
                                            navController.navigate("citizen_dashboard") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                        is AuthResult.Failure -> {
                                            generalCitizenError = authResult.message
                                        }
                                        is AuthResult.RateLimited -> {
                                            generalCitizenError = "Too many failed attempts. Locked out for ${authResult.remainingSeconds}s."
                                        }
                                        is AuthResult.AccountSuspended -> {
                                            generalCitizenError = authResult.reason
                                        }
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSubmitting) Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF233554)))
                                else Brush.horizontalGradient(listOf(ElectricCyan, ElectricBlue)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = ElectricCyan, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = if (isCitizenRegisterMode) "Register & Access Dashboard" else "Sign In as Citizen",
                                color = NavyDeep,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // --------------------- ADMIN LOGIN ---------------------
            if (selectedTab == LoginTab.ADMIN) {
                // Official Department Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF161B30))
                        .border(1.dp, NeonPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(NeonPurple)
                        )
                        Column {
                            Text(
                                text = "Restricted Administrative & Officer Portal",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Department Officers, Municipal Administrators, and Apex Council.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Official Email
                OutlinedTextField(
                    value = adminEmail,
                    onValueChange = {
                        adminEmail = it
                        adminError = null
                    },
                    label = { Text("Official Government / Admin Email *") },
                    placeholder = { Text("e.g. admin@resolvex.gov.in", color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = customFieldColors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Admin Password
                OutlinedTextField(
                    value = adminPassword,
                    onValueChange = {
                        adminPassword = it
                        adminError = null
                    },
                    label = { Text("Official Password *") },
                    singleLine = true,
                    visualTransformation = if (isAdminPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(onClick = { isAdminPasswordVisible = !isAdminPasswordVisible }) {
                            Text(
                                text = if (isAdminPasswordVisible) "HIDE" else "SHOW",
                                color = NeonPurple,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = customFieldColors()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Optional Department Security Key
                OutlinedTextField(
                    value = adminAccessKey,
                    onValueChange = { adminAccessKey = it },
                    label = { Text("Department / Municipal Ward (Optional override)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = customFieldColors()
                )

                if (adminError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = adminError ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button for Admin
                Button(
                    onClick = {
                        if (!isSubmitting) {
                            if (adminEmail.trim().isEmpty() || adminPassword.isEmpty()) {
                                adminError = "Please enter official email and password."
                                return@Button
                            }
                            isSubmitting = true
                            coroutineScope.launch {
                                delay(300)
                                val authResult = userRepo.authenticate(adminEmail.trim(), adminPassword)
                                isSubmitting = false
                                when (authResult) {
                                    is AuthResult.Success -> {
                                        val account = authResult.user
                                        if (account.role == UserRole.CITIZEN) {
                                            adminError = "Citizen accounts cannot access the administrative portal. Please use the Citizen Portal tab."
                                        } else {
                                            val finalDept = if (adminAccessKey.isNotBlank()) adminAccessKey.trim() else account.department ?: "Municipal Affairs"
                                            val session = UserSession(
                                                userId = account.id,
                                                fullName = account.fullName,
                                                email = account.email,
                                                mobile = account.mobile,
                                                occupation = account.occupation,
                                                role = account.role,
                                                department = finalDept
                                            )
                                            sessionManager.saveSession(session)
                                            onAdminLoginSuccess()
                                            navController.navigate("admin_dashboard") {
                                                popUpTo("login") { inclusive = true }
                                            }
                                        }
                                    }
                                    is AuthResult.Failure -> {
                                        adminError = authResult.message
                                    }
                                    is AuthResult.RateLimited -> {
                                        adminError = "Too many failed attempts. Locked out for ${authResult.remainingSeconds}s."
                                    }
                                    is AuthResult.AccountSuspended -> {
                                        adminError = authResult.reason
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (isSubmitting) Brush.horizontalGradient(listOf(Color(0xFF1E293B), Color(0xFF233554)))
                                else Brush.horizontalGradient(listOf(NeonPurple, ElectricBlue)),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text(
                                text = "Verify & Access Official Console",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // Forgot Password Dialog
        if (showForgotPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showForgotPasswordDialog = false },
                title = { Text("Reset Citizen Password", color = TextPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Enter your registered email and choose a new secure password (min 6 characters).",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("Registered Email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = customFieldColors()
                        )
                        OutlinedTextField(
                            value = resetNewPassword,
                            onValueChange = { resetNewPassword = it },
                            label = { Text("New Password (min 6 chars)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = customFieldColors()
                        )
                        if (resetError != null) {
                            Text(resetError ?: "", color = Color(0xFFFF5252), fontSize = 11.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (resetEmail.isBlank() || resetNewPassword.length < 6) {
                                resetError = "Enter valid email and new password of at least 6 characters."
                                return@Button
                            }
                            val res = userRepo.resetPassword(resetEmail.trim(), resetNewPassword, performedBy = resetEmail.trim())
                            if (res) {
                                showForgotPasswordDialog = false
                                Toast.makeText(context, "Password reset successfully. You can now sign in.", Toast.LENGTH_LONG).show()
                            } else {
                                resetError = "No account found with this email."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
                    ) {
                        Text("Reset Password", color = NavyDeep, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForgotPasswordDialog = false }) {
                        Text("Cancel", color = SoftCyan)
                    }
                },
                containerColor = NavyDark,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun RoleQuickChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF162544))
            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = label, color = ElectricCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun customFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = Color(0xFF233554),
    errorBorderColor = Color(0xFFFF5252),
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    errorTextColor = TextPrimary,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = TextMuted,
    errorLabelColor = Color(0xFFFF5252),
    focusedContainerColor = NavyDark,
    unfocusedContainerColor = NavyDark,
    errorContainerColor = NavyDark
)
