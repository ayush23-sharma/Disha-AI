package com.shubham.womensafety.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shubham.womensafety.R
import com.shubham.womensafety.guardiandetail.GuardianInfoViewModel
import com.shubham.womensafety.ui.components.GuardianListItem
import com.shubham.womensafety.ui.theme.ColorPrimaryDark

@Composable
fun GuardianInfoScreen(
    onNavigateToAddGuardian: () -> Unit,
    viewModel: GuardianInfoViewModel = viewModel()
) {
    val guardians by viewModel.allGuardians.observeAsState(initial = emptyList())
    val showSnackbarEvent by viewModel.showSnackBarEvent.observeAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val clearedMessage = stringResource(R.string.cleared_message)

    LaunchedEffect(showSnackbarEvent) {
        if (showSnackbarEvent == true) {
            snackbarHostState.showSnackbar(clearedMessage)
            viewModel.doneShowingSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp)
        ) {
            item {
                Button(
                    onClick = onNavigateToAddGuardian,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 30.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ColorPrimaryDark)
                ) {
                    Text(stringResource(R.string.add_guardian))
                }
            }

            items(guardians, key = { it.guardianId ?: it.hashCode() }) { guardian ->
                GuardianListItem(
                    guardian = guardian,
                    onDeleteClick = { viewModel.deleteGuardian(guardian) }
                )
            }
        }
    }
}
