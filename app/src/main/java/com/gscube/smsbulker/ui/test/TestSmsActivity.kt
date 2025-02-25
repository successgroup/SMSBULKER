package com.gscube.smsbulker.ui.test

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gscube.smsbulker.SmsBulkerApplication
import com.gscube.smsbulker.repository.SmsRepository
import com.gscube.smsbulker.utils.SmsTestUtil
import javax.inject.Inject

class TestSmsActivity : AppCompatActivity() {

    @Inject
    lateinit var smsRepository: SmsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        (application as SmsBulkerApplication).appComponent.inject(this)
        super.onCreate(savedInstanceState)

        // Test sending a single message
        val result = SmsTestUtil.testSendSms(
            smsRepository = smsRepository,
            recipient = "0502850073",
            message = "Hello! This is a test message from your SMSBULKER app.",
            sender = "CEGraceland"
        )

        // Show the result
        Toast.makeText(this, result, Toast.LENGTH_LONG).show()
        finish()
    }
}
