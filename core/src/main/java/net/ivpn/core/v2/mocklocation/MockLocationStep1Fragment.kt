package net.ivpn.core.v2.mocklocation

/*
 IVPN Android app
 https://github.com/ivpn/android-app

 Created by Oleksandr Mykhailenko.
 Copyright (c) 2021 Privatus Limited.

 This file is part of the IVPN Android app.

 The IVPN Android app is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as published by the Free
 Software Foundation, either version 3 of the License, or (at your option) any later version.

 The IVPN Android app is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 details.

 You should have received a copy of the GNU General Public License
 along with the IVPN Android app. If not, see <https://www.gnu.org/licenses/>.
*/

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import net.ivpn.core.IVPNApplication
import net.ivpn.core.R
import net.ivpn.core.common.extension.navigate
import net.ivpn.core.databinding.FragmentMockLocationStep1Binding
import net.ivpn.core.v2.dialog.DialogBuilder
import net.ivpn.core.v2.dialog.Dialogs
import net.ivpn.core.v2.MainActivity
import javax.inject.Inject


class MockLocationStep1Fragment: Fragment() {
    @Inject
    lateinit var mockLocation: MockLocationViewModel

    lateinit var binding: FragmentMockLocationStep1Binding

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_mock_location_step1, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        IVPNApplication.appComponent.provideActivityComponent().create().inject(this)
        initViews()
        initToolbar()
    }

    override fun onStart() {
        super.onStart()
        activity?.let {
            if (it is MainActivity) {
                it.setContentSecure(false)
            }
        }
    }

    private fun initViews() {
        binding.contentLayout.mocklocation = mockLocation
        binding.contentLayout.toSettings.setOnClickListener {
            toSettings()
        }
        binding.contentLayout.next.setOnClickListener {
            next()
        }

        binding.contentLayout.openGuide.setOnClickListener {
            openGuide()
        }
    }

    private fun initToolbar() {
        val navController = findNavController()
        val appBarConfiguration = AppBarConfiguration(navController.graph)

        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
    }

    private fun toSettings() {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    private fun next() {
        if (mockLocation.isDeveloperOptionsEnabled()) {
            val action = MockLocationStep1FragmentDirections.actionMockLocationStep1FragmentToMockLocationStep2Fragment()
            navigate(action)
        } else {
            DialogBuilder.createNotificationDialog(context, Dialogs.MOCK_LOCATION_DEVELOPER_OPTION_ERROR)
        }
    }

    private fun openGuide() {
        val url = "https://www.ivpn.net/knowledgebase/android/developer-options-on-the-android-phone/"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }
}