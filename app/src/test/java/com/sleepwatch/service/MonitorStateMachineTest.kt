package com.sleepwatch.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MonitorStateMachineTest {

    private lateinit var stateMachine: MonitorStateMachine

    @Before
    fun setup() {
        stateMachine = MonitorStateMachine()
    }

    @Test
    fun `initial state is IDLE`() {
        assertEquals(MonitorState.IDLE, stateMachine.state)
    }

    @Test
    fun `startMonitoring transitions from IDLE to MONITORING`() = runTest {
        stateMachine.startMonitoring()
        assertEquals(MonitorState.MONITORING, stateMachine.state)
    }

    @Test
    fun `startMonitoring does nothing if not IDLE`() = runTest {
        stateMachine.startMonitoring()
        stateMachine.startMonitoring()
        assertEquals(MonitorState.MONITORING, stateMachine.state)
    }

    @Test
    fun `screen on from MONITORING transitions to ALERTING`() = runTest {
        stateMachine.startMonitoring()
        val result = stateMachine.onScreenOn()
        assertEquals(MonitorState.ALERTING, result)
        assertEquals(MonitorState.ALERTING, stateMachine.state)
    }

    @Test
    fun `screen on from IDLE stays IDLE`() = runTest {
        val result = stateMachine.onScreenOn()
        assertEquals(MonitorState.IDLE, result)
    }

    @Test
    fun `screen off increments counter in MONITORING`() = runTest {
        stateMachine.startMonitoring()
        stateMachine.setScreenOffThreshold(3)

        stateMachine.onScreenOff()
        assertEquals(MonitorState.MONITORING, stateMachine.state)

        stateMachine.onScreenOff()
        assertEquals(MonitorState.MONITORING, stateMachine.state)

        stateMachine.onScreenOff()
        assertEquals(MonitorState.SLEEP_DETECTED, stateMachine.state)
    }

    @Test
    fun `screen off from ALERTING stays ALERTING and does not increment counter`() = runTest {
        stateMachine.startMonitoring()
        stateMachine.setScreenOffThreshold(2)
        stateMachine.onScreenOn() // -> ALERTING

        stateMachine.onScreenOff()
        assertEquals(MonitorState.ALERTING, stateMachine.state)

        stateMachine.onScreenOff()
        assertEquals(MonitorState.ALERTING, stateMachine.state)

        // Back to monitoring and screen off counter should reset
        stateMachine.backToMonitoring()
        assertEquals(MonitorState.MONITORING, stateMachine.state)
    }

    @Test
    fun `screen on from SLEEP_DETECTED resets to MONITORING`() = runTest {
        stateMachine.startMonitoring()
        stateMachine.setScreenOffThreshold(1)
        stateMachine.onScreenOff() // -> SLEEP_DETECTED

        val result = stateMachine.onScreenOn()
        assertEquals(MonitorState.MONITORING, result)
    }

    @Test
    fun `reset returns to IDLE`() = runTest {
        stateMachine.startMonitoring()
        stateMachine.onScreenOn()
        stateMachine.reset()
        assertEquals(MonitorState.IDLE, stateMachine.state)
    }

    @Test
    fun `pause resets screen off counter`() = runTest {
        stateMachine.startMonitoring()
        stateMachine.setScreenOffThreshold(3)
        stateMachine.onScreenOff()
        stateMachine.onScreenOff()
        stateMachine.pause()
        stateMachine.onScreenOff()
        assertEquals(MonitorState.MONITORING, stateMachine.state) // counter was reset, only 1 now
    }

    @Test
    fun `custom threshold works correctly`() = runTest {
        stateMachine.startMonitoring()
        stateMachine.setScreenOffThreshold(5)

        repeat(4) {
            stateMachine.onScreenOff()
            assertEquals(MonitorState.MONITORING, stateMachine.state)
        }

        stateMachine.onScreenOff()
        assertEquals(MonitorState.SLEEP_DETECTED, stateMachine.state)
    }
}
