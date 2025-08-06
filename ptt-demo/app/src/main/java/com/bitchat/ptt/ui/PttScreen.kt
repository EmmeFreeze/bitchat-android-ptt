package com.bitchat.ptt.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.ptt.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PttScreen(viewModel: PttViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Header
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PTT Demo",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        state.isRecording -> Color.Red
                        state.isTransmitting -> Color.Blue
                        state.isReceiving -> Color.Green
                        state.isPlaying -> Color.Magenta
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
        
        // Connection Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatusCard(
                title = "Connected",
                value = "${state.connectedDevices}",
                color = if (state.connectedDevices > 0) Color.Green else Color.Gray
            )
            
            StatusCard(
                title = "Audio",
                value = state.audioQuality,
                color = MaterialTheme.colorScheme.primary
            )
            
            StatusCard(
                title = "Latency",
                value = "${state.latencyMs}ms",
                color = when {
                    state.latencyMs < 1000 -> Color.Green
                    state.latencyMs < 2000 -> Color.Yellow
                    else -> Color.Red
                }
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // PTT Button
        if (state.isPermissionsGranted) {
            PttButton(
                isPressed = state.isRecording,
                onPressStart = { viewModel.onPttPressed() },
                onPressEnd = { viewModel.onPttReleased() }
            )
        } else {
            Text(
                text = "Please grant all required permissions to use PTT",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Instructions
        Text(
            text = "Hold the button to record and transmit audio",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    color: Color
) {
    Card(
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun PttButton(
    isPressed: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    var isCurrentlyPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(CircleShape)
            .background(
                if (isPressed || isCurrentlyPressed) 
                    MaterialTheme.colorScheme.error 
                else 
                    MaterialTheme.colorScheme.primary
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isCurrentlyPressed = true
                        onPressStart()
                        tryAwaitRelease()
                        isCurrentlyPressed = false
                        onPressEnd()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isPressed || isCurrentlyPressed) "RECORDING" else "HOLD TO TALK",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
