package com.wavesplatform.wallet.v2.data.manager

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.vicpin.krealmextensions.query
import com.vicpin.krealmextensions.queryFirst

import io.reactivex.Observable

//import io.realm
import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import io.realm.annotations.RealmClass

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmQuery
import io.realm.RealmResults
import com.vicpin.krealmextensions.*
import com.vicpin.krealmextensions.queryFirst

class PinStoreService {

    private val pincodesReference = FirebaseDatabase.getInstance().reference.child(PINCODES)

    fun writePassword(guid: String, passCode: String, keyPassword: String): Observable<Boolean> {

        var passCodeDatabase = PassCodeDatabase()
        passCodeDatabase.id = guid
        passCodeDatabase.keyForPassword = keyPassword
        passCodeDatabase.passcode = passCode
        PassCodeDatabase.insertPassCodeDatabase(passCodeDatabase)
        return Observable.just(true)

//        return Observable.create { emitter ->
//            pincodesReference
//                    .child(guid)
//                    .removeValue()
//                    .addOnCompleteListener { task ->
//                        pincodesReference
//                                .child(guid)
//                                .child(passCode)
//                                .setValue(keyPassword) { err, ref ->
//                                    if (err == null) {
//                                        emitter.onNext(true)
//                                        emitter.onComplete()
//                                    } else {
//                                        emitter.onError(err.toException())
//                                    }
//                                }
//                    }
//        }
    }

    fun readPassword(guid: String, passCode: String, tryCount: Int): Observable<String> {

        var passCodeDatabase = PassCodeDatabase.getPassCodeDatabase(guid)
        print(passCodeDatabase)

        return if (passCodeDatabase == null) Observable.error(IncorrectPinException())
        else
            return if (passCodeDatabase.passcode == passCode) Observable.just(passCodeDatabase.keyForPassword)
            else Observable.error(IncorrectPinException())
//        if (passCodeDatabase == null) {
//            return Observable.error(IncorrectPinException())
//        }else {
//            if (passCodeDatabase.passcode == passCode) {
//                return Observable.just(passCodeDatabase.keyForPassword)
//            }else {
//                return Observable.error(IncorrectPinException())
//            }
//        }


//        return Observable.create { emitter ->
//            pincodesReference
//                    .child(guid)
//                    .child(TRY)
//                    .child(TRY + (tryCount + 1))
//                    .setValue(passCode) { err, ref ->
//                        if (err == null) {
//                            pincodesReference
//                                    .child(guid)
//                                    .child(passCode)
//                                    .addListenerForSingleValueEvent(object : ValueEventListener {
//                                        override fun onDataChange(snap: DataSnapshot) {
//                                            if (snap.value != null) {
//                                                emitter.onNext(snap.value!!.toString())
//                                                emitter.onComplete()
//                                            } else {
//                                                emitter.onError(IncorrectPinException())
//                                            }
//                                        }
//
//                                        override fun onCancelled(error: DatabaseError) {
//                                            emitter.onError(error.toException())
//                                        }
//                                    })
//                        } else {
//                            if (err.code == DatabaseError.PERMISSION_DENIED) {
//                                emitter.onError(IncorrectPinException())
//                            } else {
//                                emitter.onError(err.toException())
//                            }
//                        }
//                    }
//        }
    }

    inner class IncorrectPinException : RuntimeException()

    companion object {

        private const val TRY = "try"
        private const val PINCODES = "pincodes"
    }
}



@RealmClass
open class PassCodeDatabase : RealmObject(){
    @PrimaryKey
    open var id:String = ""
    open var keyForPassword:String = ""
    open var passcode:String = ""

    companion object {
         fun getDB(): Realm {
            val config = RealmConfiguration.Builder().name("PassCodeDB.realm").schemaVersion(1).build()
            return Realm.getInstance(config)
        }

        //增
         fun insertPassCodeDatabase(passCodeDatabase: PassCodeDatabase) {
//            var defaultRealm = PassCodeDatabase.getDB()
//            defaultRealm.beginTransaction()
//            var realmUser = defaultRealm.copyToRealm(passCodeDatabase)
//            defaultRealm.commitTransaction()
            passCodeDatabase.save()
        }

//        //删
//         fun deletePassCodeDatabase(passCodeDatabase: PassCodeDatabase) {
//            var defaultRealm = this.getDB()
//
//        }
//
//        //改手势密码
//         fun updatePassCode(passCodeDatabase: PassCodeDatabase) {
//            var defaultRealm = this.getDB()
//            defaultRealm.executeTransaction(Realm.Transaction {
//
//                defaultRealm.copyToRealmOrUpdate(passCodeDatabase)
//            })
//
//        }

        /// 获取 指定id (主键) 的 PassCodeDatabase
         fun getPassCodeDatabase(fromId: String): PassCodeDatabase? {
//            var defaultRealm = this.getDB()
            var passCodeDatabaseList = PassCodeDatabase().query{equalTo("id",fromId)}


            return if (passCodeDatabaseList.isEmpty()) null
            else passCodeDatabaseList[0]
//            if (passCodeDatabaseList.isEmpty()){
//                return null
//            }else {
//                return passCodeDatabaseList[0]
//            }

        }
    }
}
