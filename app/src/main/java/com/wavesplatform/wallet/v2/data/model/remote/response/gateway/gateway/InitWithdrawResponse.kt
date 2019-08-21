/*
 * Created by Eduard Zaydel on 17/6/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.data.model.remote.response.gateway.gateway


import com.google.gson.annotations.SerializedName

data class InitWithdrawResponse(
        @SerializedName("recipientAddress")
        var recipientAddress: String,
        @SerializedName("processId")
        var processId: String,
        @SerializedName("fee")
        var fee: Long,
        @SerializedName("maxAmount")
        var maxAmount: Long,
        @SerializedName("minAmount")
        var minAmount: Long
)