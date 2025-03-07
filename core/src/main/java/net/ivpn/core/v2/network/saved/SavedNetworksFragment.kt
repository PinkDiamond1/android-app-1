package net.ivpn.core.v2.network.saved

/*
 IVPN Android app
 https://github.com/ivpn/android-app

 Created by Oleksandr Mykhailenko.
 Copyright (c) 2020 Privatus Limited.

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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import net.ivpn.core.IVPNApplication
import net.ivpn.core.R
import net.ivpn.core.databinding.FragmentSavedNetworksBinding
import net.ivpn.core.v2.network.NetworkRecyclerViewAdapter
import net.ivpn.core.v2.network.NetworkViewModel
import javax.inject.Inject

class SavedNetworksFragment: Fragment() {

    private lateinit var binding: FragmentSavedNetworksBinding
    private var adapter: NetworkRecyclerViewAdapter? = null

    @Inject
    lateinit var network: NetworkViewModel

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_saved_networks, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        IVPNApplication.appComponent.provideActivityComponent().create().inject(this)
        initViews()
    }

    private fun initViews() {
        adapter = NetworkRecyclerViewAdapter(activity)
        binding.viewmodel = network
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.setEmptyView(binding.emptyView)
    }
}