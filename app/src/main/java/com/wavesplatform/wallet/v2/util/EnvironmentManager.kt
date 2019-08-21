/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.util

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.preference.PreferenceManager
import com.google.gson.Gson
import com.wavesplatform.sdk.WavesSdk
import com.wavesplatform.sdk.model.response.data.AssetsInfoResponse
import com.wavesplatform.sdk.model.response.node.AssetBalanceResponse
import com.wavesplatform.sdk.model.response.node.IssueTransactionResponse
import com.wavesplatform.sdk.model.response.node.UtilsTimeResponse
import com.wavesplatform.sdk.net.service.DataService
import com.wavesplatform.sdk.net.service.NodeService
import com.wavesplatform.sdk.utils.WavesConstants
import com.wavesplatform.sdk.utils.Environment
import com.wavesplatform.sdk.utils.RxUtil
import com.wavesplatform.wallet.App
import com.wavesplatform.wallet.v2.data.Constants
import com.wavesplatform.wallet.v2.data.local.PreferencesHelper
import com.wavesplatform.wallet.v2.data.manager.GithubServiceManager
import com.wavesplatform.wallet.v2.data.model.remote.response.GlobalConfiguration
import com.wavesplatform.wallet.v2.data.remote.GithubService
import com.wavesplatform.wallet.v2.data.model.service.cofigs.GlobalConfigurationResponse
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.functions.BiFunction
import pers.victor.ext.currentTimeMillis
import timber.log.Timber
import kotlin.math.abs
import kotlin.system.exitProcess

class EnvironmentManager(var current: ClientEnvironment) {

    private var configurationDisposable: Disposable? = null
    private var versionDisposable: Disposable? = null
    private var gateWayHostInterceptor: HostInterceptor? = null

    companion object {

        private const val BASE_PROXY_CONFIG_URL = "https://github-proxy.wvservices.com/"
        private const val BASE_RAW_CONFIG_URL = "https://raw.githubusercontent.com/"

        private const val BRANCH = "mobile/v2.5"

        private const val KEY_ENV_TEST_NET = "env_testnet"
        private const val KEY_ENV_MAIN_NET = "env_prod"

        private const val FILENAME_TEST_NET = "environment_testnet.json"
        private const val FILENAME_MAIN_NET = "environment_mainnet.json"

        const val URL_CONFIG_MAIN_NET = BASE_PROXY_CONFIG_URL +
                "wavesplatform/waves-client-config/$BRANCH/environment_mainnet.json"
        const val URL_CONFIG_TEST_NET = BASE_PROXY_CONFIG_URL +
                "wavesplatform/waves-client-config/$BRANCH/environment_testnet.json"
        const val URL_COMMISSION_MAIN_NET = "/$BRANCH/fee.json"

        const val URL_RAW_CONFIG_MAIN_NET = BASE_RAW_CONFIG_URL +
                "wavesplatform/waves-client-config/$BRANCH/environment_mainnet.json"
        const val URL_RAW_CONFIG_TEST_NET = BASE_RAW_CONFIG_URL +
                "wavesplatform/waves-client-config/$BRANCH/environment_testnet.json"
        const val URL_RAW_COMMISSION_MAIN_NET = BASE_RAW_CONFIG_URL +
                "wavesplatform/waves-client-config/$BRANCH/fee.json"

        private var instance: EnvironmentManager? = null
        private val handler = Handler()

        private const val GLOBAL_CURRENT_ENVIRONMENT_DATA = "global_current_environment_data"
        private const val GLOBAL_CURRENT_ENVIRONMENT = "global_current_environment"
        private const val GLOBAL_CURRENT_TIME_CORRECTION = "global_current_time_correction"

        val netCode: Byte
            get() = environment.configuration.scheme[0].toByte()

        val vostokNetCode: Byte
            get() = environment.externalProperties.vostokNetCode.toByte()

        val globalConfiguration: GlobalConfigurationResponse
            get() = environment.configuration

        val name: String
            get() = environment.name

        @JvmStatic
        val servers: GlobalConfigurationResponse.Servers
            get() = environment.configuration.servers

        val defaultAssets = mutableListOf<AssetBalanceResponse>()

        val environment: ClientEnvironment
            get() = instance!!.current

        val environmentName: String?
            get() {
                val preferenceManager = PreferenceManager
                        .getDefaultSharedPreferences(App.getAppContext())
                return preferenceManager.getString(
                        GLOBAL_CURRENT_ENVIRONMENT, ClientEnvironment.MAIN_NET.name)
            }

        fun getTime(): Long {
            val timeCorrection = if (instance == null) {
                0L
            } else {
                PreferenceManager
                        .getDefaultSharedPreferences(App.getAppContext())
                        .getLong(GLOBAL_CURRENT_TIME_CORRECTION, 0L)
            }
            return currentTimeMillis + timeCorrection
        }

        fun setCurrentEnvironment(current: ClientEnvironment) {
            PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
                    .edit()
                    .putString(GLOBAL_CURRENT_ENVIRONMENT, current.name)
                    .remove(GLOBAL_CURRENT_ENVIRONMENT_DATA)
                    .apply()
            restartApp(App.getAppContext())
        }


        @JvmStatic
        fun update() {
            var initEnvironment: ClientEnvironment = ClientEnvironment.MAIN_NET
            for (environment in ClientEnvironment.environments) {
                if (environmentName!!.equals(environment.name, ignoreCase = true)) {
                    initEnvironment = environment
                    break
                }
            }
            instance = EnvironmentManager(initEnvironment)

            getDefaultConfig()

            val timeCorrection = PreferenceManager
                    .getDefaultSharedPreferences(App.getAppContext())
                    .getLong(GLOBAL_CURRENT_TIME_CORRECTION, 0)
            val config = getLocalSavedConfig()

            val environment = Environment(
                    Environment.Server.Custom(
                            config.servers.nodeUrl,
                            config.servers.matcherUrl,
                            config.servers.dataUrl,
                            config.scheme[0].toByte()),
                    timeCorrection)

            WavesSdk.setEnvironment(environment)

            loadConfiguration(
                    WavesSdk.service().getDataService(),
                    WavesSdk.service().getNode(),
                    GithubServiceManager.create(null))
        }

        fun getDefaultConfig(): GlobalConfiguration? {
            return when (environmentName) {
                KEY_ENV_MAIN_NET -> {
                    Gson().fromJson(
                            ClientEnvironment.loadJsonFromAsset(App.getAppContext(), FILENAME_MAIN_NET),
                            GlobalConfiguration::class.java)
                }
                KEY_ENV_TEST_NET -> {
                    Gson().fromJson(
                            ClientEnvironment.loadJsonFromAsset(App.getAppContext(), FILENAME_TEST_NET),
                            GlobalConfiguration::class.java)
                }
                else -> {
                    Gson().fromJson(
                            ClientEnvironment.loadJsonFromAsset(App.getAppContext(), FILENAME_TEST_NET),
                            GlobalConfiguration::class.java)
                }
            }
        }

        fun createGateWayHostInterceptor(): HostInterceptor {
            instance!!.gateWayHostInterceptor = HostInterceptor(servers.gatewayUrl)
            return instance!!.gateWayHostInterceptor!!
        }

        private fun loadConfiguration(dataService: DataService,
                                      nodeService: NodeService,
                                      githubService: GithubService) {
            instance!!.configurationDisposable =
                    Observable.zip(
                            githubService.globalConfiguration(environment.url),
                            nodeService.utilsTime(),
                            BiFunction { conf: GlobalConfigurationResponse, time: UtilsTimeResponse ->
                                return@BiFunction Pair(conf, time)
                            })
                            .onErrorReturn {
                                Timber.e(it, "EnvironmentManager: Can't download global configuration!")
                                val time = currentTimeMillis + PreferenceManager
                                        .getDefaultSharedPreferences(App.getAppContext())
                                        .getLong(GLOBAL_CURRENT_TIME_CORRECTION, 0L)
                                Pair(environment.configuration, UtilsTimeResponse(time, time))
                            }
                            .map { pair ->
                                val timeCorrection = pair.second.ntp - currentTimeMillis
                                setTimeCorrection(timeCorrection)
                                setConfiguration(pair.first)

                                val environment = Environment(
                                        Environment.Server.Custom(
                                                pair.first.servers.nodeUrl,
                                                pair.first.servers.matcherUrl,
                                                pair.first.servers.dataUrl,
                                                pair.first.scheme[0].toByte()),
                                        timeCorrection)

                                WavesSdk.setEnvironment(environment)
                                pair.first.servers.gatewayUrl

                                globalConfiguration.generalAssets.map { it.assetId }
                            }
                            .flatMap { dataService.assets(it) }
                            .map { info ->
                                setDefaultAssets(info)
                                instance!!.configurationDisposable!!.dispose()
                            }
                            .compose(RxUtil.applyObservableDefaultSchedulers())
                            .subscribe({
                                instance!!.configurationDisposable!!.dispose()
                            }, { error ->
                                Timber.e(error, "EnvironmentManager: Can't download GlobalConfiguration!")
                                error.printStackTrace()
                                setConfiguration(environment.configuration)
                                instance!!.configurationDisposable!!.dispose()
                            })

            instance!!.versionDisposable = githubService.loadLastAppVersion(Constants.URL_GITHUB_CONFIG_VERSION)
                    .compose(RxUtil.applyObservableDefaultSchedulers())
                    .subscribe({ version ->
                        PreferencesHelper(App.getAppContext()).lastAppVersion = version.lastVersion
                        instance!!.versionDisposable!!.dispose()
                    }, { error ->
                        error.printStackTrace()
                        instance!!.versionDisposable!!.dispose()
                    })
        }


        fun findAssetIdByAssetId(assetId: String): GlobalConfigurationResponse.ConfigAsset? {
            return instance?.current?.configuration?.generalAssets?.firstOrNull { it.assetId == assetId }
        }


        private fun getLocalSavedConfig(): GlobalConfigurationResponse {

            val preferences = PreferenceManager.getDefaultSharedPreferences(App.getAppContext())
            val currentEnvName = preferences.getString(
                    GLOBAL_CURRENT_ENVIRONMENT, ClientEnvironment.MAIN_NET.name)

            return when (currentEnvName) {
                ClientEnvironment.MAIN_NET.name -> getConfiguration(preferences, ClientEnvironment.MAIN_NET)
                ClientEnvironment.TEST_NET.name -> getConfiguration(preferences, ClientEnvironment.TEST_NET)
                else -> getConfiguration(preferences, ClientEnvironment.MAIN_NET)
            }
        }

        private fun getConfiguration(preferences: SharedPreferences, environment: ClientEnvironment)
                : GlobalConfigurationResponse {
            return if (preferences.contains(GLOBAL_CURRENT_ENVIRONMENT_DATA)) {
                val json = preferences.getString(
                        GLOBAL_CURRENT_ENVIRONMENT_DATA,
                        Gson().toJson(environment.configuration))
                Gson().fromJson(json, GlobalConfigurationResponse::class.java)
            } else {
                environment.configuration
            }
        }

        private fun setTimeCorrection(timeCorrection: Long) {
            if (abs(timeCorrection) > 30_000) {
                PreferenceManager
                        .getDefaultSharedPreferences(App.getAppContext())
                        .edit()
                        .putLong(GLOBAL_CURRENT_TIME_CORRECTION,
                                timeCorrection)
                        .apply()
            }
        }

        private fun setDefaultAssets(info: AssetsInfoResponse) {
            defaultAssets.clear()
            for (assetInfo in info.data) {
                val assetBalance = AssetBalanceResponse(
                        assetId = if (assetInfo.assetInfo.id == WavesConstants.WAVES_ASSET_ID_FILLED) {
                            WavesConstants.WAVES_ASSET_ID_EMPTY
                        } else {
                            assetInfo.assetInfo.id
                        },
                        quantity = assetInfo.assetInfo.quantity,
                        isFavorite = assetInfo.assetInfo.id == WavesConstants.WAVES_ASSET_ID_FILLED,
                        issueTransaction = IssueTransactionResponse(
                                id = assetInfo.assetInfo.id,
                                assetId = assetInfo.assetInfo.id,
                                name = findAssetIdByAssetId(
                                        assetInfo.assetInfo.id)?.displayName
                                        ?: assetInfo.assetInfo.name,
                                decimals = assetInfo.assetInfo.precision,
                                quantity = assetInfo.assetInfo.quantity,
                                description = assetInfo.assetInfo.description,
                                sender = assetInfo.assetInfo.sender,
                                timestamp = assetInfo.assetInfo.timestamp.time),
                        isGateway = findAssetIdByAssetId(
                                assetInfo.assetInfo.id)?.isGateway ?: false,
                        isFiatMoney = findAssetIdByAssetId(
                                assetInfo.assetInfo.id)?.isFiat ?: false)
                defaultAssets.add(assetBalance)
            }
        }

        private fun setConfiguration(globalConfiguration: GlobalConfigurationResponse) {
            if (instance!!.gateWayHostInterceptor == null) {
                createGateWayHostInterceptor()
            }
            instance!!.gateWayHostInterceptor!!.setHost(globalConfiguration.servers.gatewayUrl)
            PreferenceManager
                    .getDefaultSharedPreferences(App.getAppContext())
                    .edit()
                    .putString(GLOBAL_CURRENT_ENVIRONMENT_DATA,
                            Gson().toJson(globalConfiguration))
                    .apply()
            instance!!.current.configuration = globalConfiguration
        }

        private fun restartApp(application: Application) {
            handler.postDelayed({
                val packageManager = application.packageManager
                val intent = packageManager.getLaunchIntentForPackage(application.packageName)
                if (intent != null) {
                    val componentName = intent.component
                    val mainIntent = Intent.makeRestartActivityTask(componentName)
                    application.startActivity(mainIntent)
                    exitProcess(0)
                }
            }, 300)
        }
    }
}