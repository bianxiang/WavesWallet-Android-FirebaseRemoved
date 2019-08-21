package com.wavesplatform.wallet.v2.data.model.db

import com.google.gson.annotations.SerializedName
import com.wavesplatform.sdk.model.response.node.TransferResponse
import com.wavesplatform.sdk.utils.notNull
import io.realm.RealmList
import io.realm.RealmModel
import io.realm.annotations.RealmClass

@RealmClass
open class TransferDb(
        @SerializedName("recipient")
        var recipient: String = "",
        @SerializedName("recipientAddress")
        var recipientAddress: String? = "",
        @SerializedName("amount")
        var amount: Long = 0
) : RealmModel {

    constructor(transfer: TransferResponse) : this() {
        transfer.notNull {
            this.recipient = it.recipient
            this.recipientAddress = it.recipientAddress
            this.amount = it.amount
        }
    }

    fun convertFromDb(): TransferResponse {
        return TransferResponse(
                recipient = this.recipient,
                recipientAddress = this.recipientAddress,
                amount = this.amount)
    }

    companion object {

        fun convertToDb(transfers: List<TransferResponse>): RealmList<TransferDb> {
            val list = RealmList<TransferDb>()
            transfers.forEach {
                list.add(TransferDb(it))
            }
            return list
        }

        fun convertFromDb(transfers: RealmList<TransferDb>): MutableList<TransferResponse> {
            val list = mutableListOf<TransferResponse>()
            transfers.forEach {
                list.add(it.convertFromDb())
            }
            return list
        }
    }
}