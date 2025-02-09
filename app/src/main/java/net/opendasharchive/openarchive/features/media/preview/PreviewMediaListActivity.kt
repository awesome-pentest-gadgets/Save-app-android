package net.opendasharchive.openarchive.features.media.preview

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.WindowManager
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import net.opendasharchive.openarchive.R
import net.opendasharchive.openarchive.databinding.ActivityPreviewMediaBinding
import net.opendasharchive.openarchive.db.Media
import net.opendasharchive.openarchive.db.Space
import net.opendasharchive.openarchive.db.WebDAVModel
import net.opendasharchive.openarchive.features.core.BaseActivity
import net.opendasharchive.openarchive.features.media.list.MediaListFragment
import net.opendasharchive.openarchive.util.AlertHelper
import net.opendasharchive.openarchive.util.Prefs

class PreviewMediaListActivity : BaseActivity() {

    private lateinit var mBinding: ActivityPreviewMediaBinding
    private var mFrag: MediaListFragment? = null

    private lateinit var viewModel: PreviewMediaListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        mBinding = ActivityPreviewMediaBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        val context = requireNotNull(application)
        val viewModelFactory = PreviewMediaListViewModelFactory(context)
        viewModel = ViewModelProvider(this, viewModelFactory)[PreviewMediaListViewModel::class.java]
        viewModel.observeValuesForWorkState(this)
        initLayout()
        showFirstTimeBatch()
    }

    private fun initLayout() {
        setSupportActionBar(mBinding.toolbar)
        supportActionBar?.apply {
            title = resources.getString(R.string.title_activity_batch_media_review)
            setDisplayHomeAsUpEnabled(true)
        }
        mFrag =
            supportFragmentManager.findFragmentById(R.id.fragUploadManager) as? MediaListFragment
    }

    override fun onResume() {
        super.onResume()
        mFrag?.let {
            it.refresh()
            it.stopBatchMode()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_batch_review_media, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_upload -> {
                batchUpload()
                return true
            }
            android.R.id.home -> {
                //NavUtils.navigateUpFromSameTask(this);
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun batchUpload() {
        val listMedia = mFrag?.getMediaList() ?: listOf()
        if (Space.getCurrent()?.tType == Space.Type.WEBDAV) {

             if(Space.getCurrent()?.host?.contains("https://sam.nl.tab.digital") == true){
                 //currently ticket #319 only supports nextcloud. Need to figure out a solution on the webDAV layer, that works across the board.
                 val availableSpace = getAvailableStorageSpace(listMedia)
                 val totalUploadsContent = availableSpace.first
                 val totalStorage = availableSpace.second
                 if(totalStorage < totalUploadsContent){
                     Toast.makeText(this, getString(R.string.upload_files_error), Toast.LENGTH_SHORT).show()
                 }else{
                     performBatchUpload(listMedia)
                 }
            }else {
                 //for NON nextcloud providers
                 performBatchUpload(listMedia)
             }
        }else{
            //for non webDAV protocols.
            performBatchUpload(listMedia)
        }
    }

    private fun performBatchUpload(listMedia: List<Media>) {
        for (media in listMedia) {
            media.sStatus = Media.Status.Queued
            media.save()
        }
        viewModel.applyMedia()
    }

    private fun getAvailableStorageSpace(listMedia: List<Media>): Pair<Double, Long> {
        val nextCloudModel = Gson().fromJson(Prefs.getNextCloudModel(), WebDAVModel::class.java)
        var totalUploadsContent = 0.0
        for (media in listMedia) {
            totalUploadsContent += media.contentLength
        }

        val totalStorage = nextCloudModel.ocs.data.quota.total - nextCloudModel.ocs.data.quota.used
        return Pair(totalUploadsContent, totalStorage)
    }

    private fun showFirstTimeBatch() {
        if ((mFrag?.getMediaList() ?: listOf()).isEmpty() || Prefs.getBoolean("ft.batch")) return

        AlertHelper.show(this, R.string.popup_batch_desc, R.string.popup_batch_title)

        Prefs.putBoolean("ft.batch", true)
    }
}