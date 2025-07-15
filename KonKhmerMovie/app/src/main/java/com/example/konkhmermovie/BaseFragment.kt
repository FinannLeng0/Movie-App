package com.example.konkhmermovie

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar

abstract class BaseFragment : Fragment() {
    private var snackbar: Snackbar? = null
    private lateinit var networkUtils: NetworkUtils

    fun observeNetwork(view: View) {
        networkUtils = NetworkUtils(requireContext())
        networkUtils.observe(viewLifecycleOwner) { isConnected ->
            if (isConnected) {
                snackbar?.dismiss()
            } else {
                snackbar = Snackbar.make(view, "No Internet Connection", Snackbar.LENGTH_INDEFINITE)
                    .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
                    .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                snackbar?.show()
            }
        }
    }
}
