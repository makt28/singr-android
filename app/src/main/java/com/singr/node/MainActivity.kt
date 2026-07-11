package com.singr.node

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val pager = findViewById<ViewPager2>(R.id.pager)
        val tabs = findViewById<TabLayout>(R.id.tabs)

        pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> NodesFragment()
                1 -> PortHopFragment()
                else -> DdnsFragment()
            }
        }
        pager.offscreenPageLimit = 2

        TabLayoutMediator(tabs, pager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.tab_nodes)
                1 -> getString(R.string.tab_porthop)
                else -> getString(R.string.tab_ddns)
            }
        }.attach()
    }
}
