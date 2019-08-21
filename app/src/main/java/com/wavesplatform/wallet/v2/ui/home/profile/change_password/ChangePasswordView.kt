/*
 * Created by Eduard Zaydel on 1/4/2019
 * Copyright © 2019 Waves Platform. All rights reserved.
 */

package com.wavesplatform.wallet.v2.ui.home.profile.change_password

import com.wavesplatform.wallet.v2.ui.base.view.BaseMvpView

interface ChangePasswordView : BaseMvpView {
    fun afterSuccessChangePassword()
}
