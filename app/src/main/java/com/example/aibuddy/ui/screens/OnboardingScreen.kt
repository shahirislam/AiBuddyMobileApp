package com.example.aibuddy.ui.screens

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.aibuddy.AppDestinations
import com.example.aibuddy.viewmodel.OnboardingViewModel
import com.example.aibuddy.viewmodel.OnboardingStep
import com.google.accompanist.flowlayout.FlowRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(
    navController: NavController,
    onboardingViewModel: OnboardingViewModel = viewModel()
) {
    val onboardingStep by onboardingViewModel.onboardingStep.collectAsState()
    val context = LocalContext.current

    AnimatedContent(targetState = onboardingStep) { step ->
        when (step) {
            is OnboardingStep.Name -> NameStep(onboardingViewModel)
            is OnboardingStep.Country -> CountryStep(onboardingViewModel)
            is OnboardingStep.Occupation -> OccupationStep(onboardingViewModel)
            is OnboardingStep.Interests -> InterestsStep(onboardingViewModel)
            is OnboardingStep.Passion -> PassionStep(onboardingViewModel)
            is OnboardingStep.Finished -> {
                val sharedPreferences = context.getSharedPreferences("aibuddy_prefs", Context.MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putBoolean("onboarding_complete", true)
                    apply()
                }
                navController.navigate(AppDestinations.HOME_ROUTE) {
                    popUpTo(AppDestinations.ONBOARDING_ROUTE) { inclusive = true }
                }
            }
        }
    }
}

@Composable
private fun NameStep(viewModel: OnboardingViewModel) {
    val name by viewModel.userName.collectAsState()
    OnboardingStepScaffold(question = "Hello! What should I call you?") {
        OutlinedTextField(
            value = name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("Your Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.saveName() })
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.saveName() }, enabled = name.isNotBlank()) {
            Text("Continue")
        }
    }
}

@Composable
private fun CountryStep(viewModel: OnboardingViewModel) {
    val country by viewModel.country.collectAsState()
    OnboardingStepScaffold(question = "Where in the world are you from?") {
        OutlinedTextField(
            value = country,
            onValueChange = viewModel::onCountryChanged,
            label = { Text("Country (Optional)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.saveCountry() })
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.saveCountry() }) {
            Text("Next")
        }
    }
}

@Composable
private fun OccupationStep(viewModel: OnboardingViewModel) {
    val occupation by viewModel.occupation.collectAsState()
    OnboardingStepScaffold(question = "What do you do for a living?") {
        OutlinedTextField(
            value = occupation,
            onValueChange = viewModel::onOccupationChanged,
            label = { Text("Your Occupation (e.g., Student, Engineer)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.saveOccupation() })
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.saveOccupation() }) {
            Text("Next")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InterestsStep(viewModel: OnboardingViewModel) {
    val interests by viewModel.interests.collectAsState()
    var currentInterest by remember { mutableStateOf("") }

    OnboardingStepScaffold(question = "What are some of your interests?") {
        OutlinedTextField(
            value = currentInterest,
            onValueChange = { currentInterest = it },
            label = { Text("Add an interest (e.g., Hiking, Coding)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (currentInterest.isNotBlank()) {
                    viewModel.addInterest(currentInterest)
                    currentInterest = ""
                }
            }),
            trailingIcon = {
                IconButton(onClick = {
                    if (currentInterest.isNotBlank()) {
                        viewModel.addInterest(currentInterest)
                        currentInterest = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Interest")
                }
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            mainAxisSpacing = 8.dp,
            crossAxisSpacing = 8.dp
        ) {
            interests.forEach { interest ->
                InputChip(
                    selected = false,
                    onClick = { viewModel.removeInterest(interest) },
                    label = { Text(interest) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove $interest",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.saveInterests() }) {
            Text("Next")
        }
    }
}

@Composable
private fun PassionStep(viewModel: OnboardingViewModel) {
    val passion by viewModel.passion.collectAsState()
    OnboardingStepScaffold(question = "What's something you're truly passionate about?") {
        OutlinedTextField(
            value = passion,
            onValueChange = viewModel::onPassionChanged,
            label = { Text("Your Passion (e.g., 'Learning new languages')") },
            modifier = Modifier.height(120.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { viewModel.savePassionAndFinish() }) {
            Text("All Done!")
        }
    }
}

@Composable
private fun OnboardingStepScaffold(
    question: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = question,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(32.dp))
            content()
        }
    }
}
