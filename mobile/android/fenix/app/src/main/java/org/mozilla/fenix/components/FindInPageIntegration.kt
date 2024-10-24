/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.components

import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.core.view.isVisible
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.findinpage.FindInPageFeature
import mozilla.components.feature.findinpage.view.FindInPageBar
import mozilla.components.support.base.feature.LifecycleAwareFeature
import mozilla.components.support.base.feature.UserInteractionHandler
import org.mozilla.fenix.R
import org.mozilla.fenix.components.appstate.AppAction.FindInPageAction
import org.mozilla.fenix.components.appstate.AppState

/**
 * BrowserFragment delegate to handle all layout updates needed to show or hide the find in page bar.
 *
 * @param store The [BrowserStore] used to look up the current selected tab.
 * @param appStore The [AppStore] used to update the [AppState.showFindInPage] state.
 * @param sessionId ID of the [store] session in which the query will be performed.
 * @param view The [FindInPageBar] view to display.
 * @param engineView the browser in which the queries will be made and which needs to be better positioned
 * to suit the find in page bar.
 * @param toolbars [View]s that the find in page bar will hide/show while it is being displayed/hidden.
 * @param findInPageHeight The height of the find in page bar.
 */
class FindInPageIntegration(
    private val store: BrowserStore,
    private val appStore: AppStore,
    private val sessionId: String? = null,
    private val view: FindInPageBar,
    private val engineView: EngineView,
    private val toolbars: List<ViewGroup?>,
    private val findInPageHeight: Int = view.context.resources.getDimensionPixelSize(R.dimen.browser_toolbar_height),
) : LifecycleAwareFeature, UserInteractionHandler {
    @VisibleForTesting
    internal val feature by lazy { FindInPageFeature(store, view, engineView, ::onClose) }
    private var engineViewLayoutData: EngineViewLayoutData? = null

    override fun start() {
        feature.start()
    }

    override fun stop() {
        feature.stop()
        appStore.dispatch(FindInPageAction.FindInPageDismissed)
    }

    override fun onBackPressed(): Boolean {
        return feature.onBackPressed()
    }

    private fun onClose() {
        view.visibility = View.GONE
        restorePreviousLayout()
        appStore.dispatch(FindInPageAction.FindInPageDismissed)
    }

    /**
     * Start the find in page functionality.
     */
    @UiThread
    fun launch() {
        onLaunch(view, feature)
    }

    private fun onLaunch(view: View, feature: LifecycleAwareFeature) {
        store.state.findCustomTabOrSelectedTab(sessionId)?.let { tab ->
            prepareLayoutForFindBar()

            view.visibility = View.VISIBLE
            (feature as FindInPageFeature).bind(tab)
            view.layoutParams.height = findInPageHeight
        }
    }

    private fun restorePreviousLayout() {
        toolbars.forEach { it?.isVisible = true }
        restoreEngineViewLayoutData()
    }

    private fun prepareLayoutForFindBar() {
        storeEngineViewLayoutData()
        toolbars.forEach { it?.isVisible = false }
        expandEngineView()
    }

    private fun storeEngineViewLayoutData() {
        val engineViewLayoutParams = getEngineViewsLayoutParams()
        engineViewLayoutData = EngineViewLayoutData(
            topMargin = engineViewLayoutParams.topMargin,
            bottomMargin = engineViewLayoutParams.bottomMargin,
            translationY = getEngineViewParent().translationY,
        )
    }

    private fun restoreEngineViewLayoutData() {
        engineViewLayoutData?.let {
            val engineViewLayoutParams = getEngineViewsLayoutParams()
            engineViewLayoutParams.topMargin = it.topMargin
            engineViewLayoutParams.bottomMargin = it.bottomMargin
            getEngineViewParent().translationY = it.translationY
        }
        engineView.setDynamicToolbarMaxHeight(toolbars.sumOf { it?.height ?: 0 })
    }

    private fun expandEngineView() {
        // Ensure the webpage occupies all screen estate minus the find in page bar.
        val layoutParams = getEngineViewsLayoutParams()
        layoutParams.topMargin = 0
        layoutParams.bottomMargin = findInPageHeight
        getEngineViewParent().translationY = 0f
        engineView.setDynamicToolbarMaxHeight(findInPageHeight)
    }

    private fun getEngineViewParent() = engineView.asView().parent as View

    private fun getEngineViewsLayoutParams() = getEngineViewParent().layoutParams as MarginLayoutParams

    private data class EngineViewLayoutData(
        val topMargin: Int,
        val bottomMargin: Int,
        val translationY: Float,
    )
}
