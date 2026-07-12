package com.example.pepper_person_id_poc.application.contract

import com.example.pepper_person_id_poc.domain.device.DeviceDiagnostics

interface DeviceDiagnosticsProvider {
    fun collect(): DeviceDiagnostics
}
