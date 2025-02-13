package net.opendasharchive.openarchive.services.dropbox

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import com.dropbox.core.android.Auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import net.opendasharchive.openarchive.MainActivity
import net.opendasharchive.openarchive.R
import net.opendasharchive.openarchive.databinding.ActivityLoginDropboxBinding
import net.opendasharchive.openarchive.db.Space
import net.opendasharchive.openarchive.features.core.BaseActivity
import net.opendasharchive.openarchive.services.SaveClient
import net.opendasharchive.openarchive.util.AlertHelper
import net.opendasharchive.openarchive.util.Constants.DROPBOX_HOST
import net.opendasharchive.openarchive.util.Constants.DROPBOX_NAME
import net.opendasharchive.openarchive.util.Constants.DROPBOX_USERNAME
import net.opendasharchive.openarchive.util.Constants.SPACE_EXTRA
import net.opendasharchive.openarchive.util.Prefs
import net.opendasharchive.openarchive.util.extensions.show


class DropboxLoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginDropboxBinding
    private lateinit var mSpace: Space
    private var isNewSpace: Boolean = false
    private var isTokenExist: Boolean = false
    private var isSuccessLogin: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        binding = ActivityLoginDropboxBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigate()
            }
        })

        if (intent.hasExtra(SPACE_EXTRA)) {
            mSpace = Space.get(intent.getLongExtra(SPACE_EXTRA, -1L)) ?: Space()
            binding.removeSpaceBt.show()
        }
        else {
            isNewSpace = true
            if (Auth.getOAuth2Token() != null) {
                isTokenExist = true
            }
            mSpace = Space()
            if (mSpace.password.isEmpty()) attemptLogin()
        }

        binding.email.text = mSpace.username

        binding.removeSpaceBt.setOnClickListener {
            removeProject()
        }
    }

    override fun onPause() {
        super.onPause()
        isTokenExist = false
    }

    override fun onResume() {
        super.onResume()

        val accessToken = Auth.getOAuth2Token()

        if (!isNewSpace || isTokenExist || accessToken.isNullOrEmpty()) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = SaveClient.getDropbox(this@DropboxLoginActivity, accessToken)

                val username: String = try {
                    client.users()?.currentAccount?.email ?: ""
                }
                catch (e: Exception) {
                    Auth.getUid() ?: ""
                }

                mSpace.username = username
                mSpace.password = accessToken
                mSpace.save()
                Prefs.setCurrentSpaceId(mSpace.id)

                MainScope().launch {
                    binding.email.text = mSpace.username
                    binding.removeSpaceBt.show()
                    isSuccessLogin = true
                }
            }
            catch (e: Exception) {
                MainScope().launch {
                    Toast.makeText(applicationContext, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private fun attemptLogin() {
        // Store values at the time of the login attempt.
        mSpace.tType = Space.Type.DROPBOX
        mSpace.name = DROPBOX_NAME
        mSpace.host = DROPBOX_HOST
        mSpace.username = DROPBOX_USERNAME

        // Show a progress spinner, and kick off a background task to
        // perform the user login attempt.
        Auth.startOAuth2Authentication(this, "gd5sputfo57s1l1")
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            navigate()

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun navigate() {
        if (isSuccessLogin) {
            finishAffinity()
            startActivity(Intent(this@DropboxLoginActivity, MainActivity::class.java))
        }
        else {
            finish()
        }
    }

    private fun removeProject() {
        AlertHelper.show(this, R.string.confirm_remove_space, R.string.remove_from_app, buttons = listOf(
            AlertHelper.positiveButton(R.string.action_remove) { _, _ ->
                mSpace.delete()

                Space.navigate(this)
            },
            AlertHelper.negativeButton()))
    }
}