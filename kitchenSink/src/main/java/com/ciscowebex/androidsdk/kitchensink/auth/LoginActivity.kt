package com.ciscowebex.androidsdk.kitchensink.auth

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.ciscowebex.androidsdk.auth.OAuthWebViewAuthenticator
import com.ciscowebex.androidsdk.kitchensink.KitchenSinkApp
import com.ciscowebex.androidsdk.kitchensink.R
import com.ciscowebex.androidsdk.kitchensink.databinding.ActivityLoginBinding
import com.ciscowebex.androidsdk.kitchensink.utils.Constants
import com.ciscowebex.androidsdk.kitchensink.utils.SharedPrefUtils
import com.ciscowebex.androidsdk.kitchensink.utils.SharedPrefUtils.getLoginTypePref

class LoginActivity : AppCompatActivity() {
    lateinit var binding: ActivityLoginBinding

    enum class LoginType(var value: String) {
        OAuth("OAuth"),
        JWT("JWT")
    }

    private var loginTypeCalled = LoginType.OAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityLoginBinding>(this, R.layout.activity_login)
                .also { binding = it }
                .apply {

                    val type = getLoginTypePref(this@LoginActivity)

                    when (type) {
                        LoginType.JWT.value -> {
                            loginTypeCalled = LoginType.JWT
                            (application as KitchenSinkApp).loadKoinModules(loginTypeCalled)
                            startActivity(Intent(this@LoginActivity, JWTLoginActivity::class.java))
                            finish()
                        }
                        LoginType.OAuth.value -> {
                            loginTypeCalled = LoginType.OAuth
                            (application as KitchenSinkApp).loadKoinModules(loginTypeCalled)
                            startActivity(Intent(this@LoginActivity, OAuthLoginActivity::class.java))
                            finish()
                        }
                    }

                    btnJwtLogin.setOnClickListener {
                        buttonClicked(LoginType.JWT)
                    }

                    btnOauthLogin.setOnClickListener {
                        buttonClicked(LoginType.OAuth)
                    }
                }
    }

    private fun buttonClicked(type: LoginType) {
        loginTypeCalled = type
        binding.loginButtonLayout.visibility = View.GONE
        binding.loginFailedTextView.visibility = View.GONE
        binding.btnJwtLogin.visibility = View.GONE

        when (type) {
            LoginType.JWT -> {
                startJWTActivity()
            }
            LoginType.OAuth -> {
                startOAuthActivity()
            }
        }
    }

    private fun startOAuthActivity() {
        (application as KitchenSinkApp).loadKoinModules(loginTypeCalled)
        startActivity(Intent(this@LoginActivity, OAuthLoginActivity::class.java))
        finish()
    }

    private fun startJWTActivity() {
        (application as KitchenSinkApp).loadKoinModules(loginTypeCalled)
        startActivity(Intent(this@LoginActivity, JWTLoginActivity::class.java))
        finish()
    }
}