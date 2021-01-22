package com.ciscowebex.androidsdk.kitchensink.auth

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import com.ciscowebex.androidsdk.kitchensink.BaseActivity
import com.ciscowebex.androidsdk.kitchensink.HomeActivity
import com.ciscowebex.androidsdk.kitchensink.KitchenSinkApp
import com.ciscowebex.androidsdk.kitchensink.R
import com.ciscowebex.androidsdk.kitchensink.databinding.ActivityJwtBinding
import com.ciscowebex.androidsdk.kitchensink.utils.showDialogWithMessage
import org.koin.android.viewmodel.ext.android.viewModel

class JWTLoginActivity : AppCompatActivity() {

    lateinit var binding: ActivityJwtBinding
    private val loginViewModel: LoginViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityJwtBinding>(this, R.layout.activity_jwt)
                .also { binding = it }
                .apply {
                    progressLayout.visibility = View.VISIBLE
                    loginButton.setOnClickListener {
                        binding.loginFailedTextView.visibility = View.GONE
                        if (jwtTokenText.text.isEmpty()) {
                            showDialogWithMessage(this@JWTLoginActivity, R.string.error_occurred, resources.getString(R.string.jwt_login_token_empty_error))
                        }
                        else {
                            binding.loginButton.visibility = View.GONE
                            progressLayout.visibility = View.VISIBLE
                            val token = jwtTokenText.text.toString()
                            loginViewModel.loginWithJWT(token)
                        }
                    }

                    loginViewModel.isAuthorized.observe(this@JWTLoginActivity, Observer { isAuthorized ->
                        progressLayout.visibility = View.GONE
                        isAuthorized?.let {
                            if (it) {
                                onLoggedIn()
                            } else {
                                onLoginFailed()
                            }
                        }
                    })

                    loginViewModel.isAuthorizedCached.observe(this@JWTLoginActivity, Observer { isAuthorizedCached ->
                        progressLayout.visibility = View.GONE
                        isAuthorizedCached?.let {
                            if (it) {
                                onLoggedIn()
                            } else {
                                jwtTokenText.visibility = View.VISIBLE
                                loginButton.visibility = View.VISIBLE
                                loginFailedTextView.visibility = View.GONE
                            }
                        }
                    })

                    loginViewModel.attemptToLoginWithCachedUser()
                }
    }

    override fun onBackPressed() {
        (application as KitchenSinkApp).closeApplication()
    }

    private fun onLoggedIn() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private fun onLoginFailed() {
        binding.loginButton.visibility = View.VISIBLE
        binding.loginFailedTextView.visibility = View.VISIBLE
    }
}