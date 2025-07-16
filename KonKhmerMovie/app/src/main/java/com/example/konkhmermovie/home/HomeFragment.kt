package com.example.konkhmermovie.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konkhmermovie.R
import com.example.konkhmermovie.databinding.FragmentHomeBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var movieAdapterPopular: MovieAdapter
    private lateinit var movieAdapterKhmer: MovieAdapter
    private lateinit var movieAdapterAnime: MovieAdapter

    private var snackbar: Snackbar? = null
    private var hasShownConnectedOnce = false
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val sliderHandler = Handler(Looper.getMainLooper())
    private var slideDirectionForward = true

    private val sliderRunnable = Runnable {
        val itemCount = bannerAdapter.itemCount
        if (itemCount > 0) {
            val currentItem = binding.bannerViewPager.currentItem
            val nextItem = if (slideDirectionForward) currentItem + 1 else currentItem - 1

            if (nextItem >= itemCount) {
                slideDirectionForward = false
                binding.bannerViewPager.currentItem = currentItem - 1
            } else if (nextItem < 0) {
                slideDirectionForward = true
                binding.bannerViewPager.currentItem = currentItem + 1
            } else {
                binding.bannerViewPager.currentItem = nextItem
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fadeIn = AnimationUtils.loadAnimation(requireContext(), R.anim.fragment_fade_in)
        view.startAnimation(fadeIn)

        val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
        bottomNav?.visibility = View.VISIBLE
        bottomNav?.animate()?.alpha(1f)?.setDuration(200)?.start()

        setupNetworkCallback()

        bannerAdapter = BannerAdapter()
        binding.bannerViewPager.adapter = bannerAdapter
        viewModel.banners.observe(viewLifecycleOwner) {
            bannerAdapter.submitList(it)
            startAutoSlide()
        }

        movieAdapterPopular = MovieAdapter { movie ->
            val action = HomeFragmentDirections.actionHomeFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" },
                movie.title.ifEmpty { "No Title" },
                movie.imageUrl.ifEmpty { "" },
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        movieAdapterKhmer = MovieAdapter { movie ->
            val action = HomeFragmentDirections.actionHomeFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" },
                movie.title.ifEmpty { "No Title" },
                movie.imageUrl.ifEmpty { "" },
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        movieAdapterAnime = MovieAdapter { movie ->
            val action = HomeFragmentDirections.actionHomeFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" },
                movie.title.ifEmpty { "No Title" },
                movie.imageUrl.ifEmpty { "" },
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        binding.popularRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.khmerRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.animeRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.popularRecyclerView.adapter = movieAdapterPopular
        binding.khmerRecyclerView.adapter = movieAdapterKhmer
        binding.animeRecyclerView.adapter = movieAdapterAnime

        viewModel.popularMovies.observe(viewLifecycleOwner) {
            movieAdapterPopular.submitList(it)
        }
        viewModel.khmerMovies.observe(viewLifecycleOwner) {
            movieAdapterKhmer.submitList(it)
        }
        viewModel.animeMovies.observe(viewLifecycleOwner) {
            movieAdapterAnime.submitList(it)
        }

        val categoryButtons = listOf(
            binding.btnAction,
            binding.btnComedy,
            binding.btnDrama,
            binding.btnRomance,
            binding.btnHorror
        )

        categoryButtons.forEach {
            it.isSelected = false
            updateButtonStyle(it, false)
        }

        categoryButtons.forEach { button ->
            button.setOnClickListener {
                categoryButtons.forEach { btn ->
                    btn.isSelected = false
                    updateButtonStyle(btn, false)
                }
                button.isSelected = true
                updateButtonStyle(button, true)
            }
        }

        binding.profileIcon.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                findNavController().navigate(R.id.action_homeFragment_to_profileSuccessFragment)
            } else {
                findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
            }
        }
    }

    private fun setupNetworkCallback() {
        connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity?.runOnUiThread {
                    snackbar?.dismiss()
                    if (!hasShownConnectedOnce) {
                        hasShownConnectedOnce = true
                        val coordinatorLayout = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
                        Snackbar.make(coordinatorLayout, "Internet Connected", Snackbar.LENGTH_SHORT)
                            .setAnchorView(R.id.bottom_navigation)
                            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.success_green))
                            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
                            .show()
                    }
                }
            }

            override fun onLost(network: Network) {
                activity?.runOnUiThread {
                    hasShownConnectedOnce = false
                    showNoInternetSnackbar()
                }
            }
        }

        connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)

        if (!isNetworkConnected()) {
            hasShownConnectedOnce = false
            showNoInternetSnackbar()
        } else {
            hasShownConnectedOnce = true
        }
    }

    private fun isNetworkConnected(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun showNoInternetSnackbar() {
        if (snackbar?.isShown == true) return

        val coordinatorLayout = requireActivity().findViewById<View>(R.id.coordinatorLayoutRoot)
        snackbar = Snackbar.make(coordinatorLayout, "No Internet Connection", Snackbar.LENGTH_LONG)
            .setAnchorView(R.id.bottom_navigation)
            .setBackgroundTint(ContextCompat.getColor(requireContext(), R.color.design_default_color_error))
            .setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        snackbar?.show()
    }

    private fun updateButtonStyle(button: Button, selected: Boolean) {
        if (selected) {
            button.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            button.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        } else {
            button.setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.transparent))
            button.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))
        }
    }

    private fun startAutoSlide() {
        sliderHandler.removeCallbacks(sliderRunnable)
        sliderHandler.postDelayed(sliderRunnable, 4000)

        binding.bannerViewPager.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                sliderHandler.removeCallbacks(sliderRunnable)
                sliderHandler.postDelayed(sliderRunnable, 4000)
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        sliderHandler.removeCallbacks(sliderRunnable)
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (_: Exception) {
        }
    }
}
