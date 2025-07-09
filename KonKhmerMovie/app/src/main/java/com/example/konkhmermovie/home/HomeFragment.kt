package com.example.konkhmermovie.home

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.konkhmermovie.databinding.FragmentHomeBinding
import com.example.konkhmermovie.home.HomeFragmentDirections
import com.google.firebase.auth.FirebaseAuth
import com.example.konkhmermovie.R

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by viewModels()

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var movieAdapterPopular: MovieAdapter
    private lateinit var movieAdapterKhmer: MovieAdapter
    private lateinit var movieAdapterAnime: MovieAdapter

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

        // Banner setup
        bannerAdapter = BannerAdapter()
        binding.bannerViewPager.adapter = bannerAdapter
        viewModel.banners.observe(viewLifecycleOwner) {
            bannerAdapter.submitList(it)
            startAutoSlide()
        }


        // MovieAdapters with correct argument order for navigation
        movieAdapterPopular = MovieAdapter { movie ->
            val action = HomeFragmentDirections.actionHomeFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" }, // description first
                movie.title.ifEmpty { "No Title" },             // title second
                movie.imageUrl.ifEmpty { "" },                   // imageUrl third
                movie.videoUrl.ifEmpty { "" }                    // videoUrl fourth
            )
            findNavController().navigate(action)
        }


        movieAdapterKhmer = MovieAdapter { movie ->
            val action = HomeFragmentDirections.actionHomeFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" }, // description first
                movie.title.ifEmpty { "No Title" },             // title second
                movie.imageUrl.ifEmpty { "" },                   // imageUrl third
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        movieAdapterAnime = MovieAdapter { movie ->
            val action = HomeFragmentDirections.actionHomeFragmentToMovieDetailFragment(
                movie.description.ifEmpty { "No Description" }, // description first
                movie.title.ifEmpty { "No Title" },             // title second
                movie.imageUrl.ifEmpty { "" },                   // imageUrl third
                movie.videoUrl.ifEmpty { "" }
            )
            findNavController().navigate(action)
        }

        // Set LayoutManagers
        binding.popularRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.khmerRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.animeRecyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        // Set adapters
        binding.popularRecyclerView.adapter = movieAdapterPopular
        binding.khmerRecyclerView.adapter = movieAdapterKhmer
        binding.animeRecyclerView.adapter = movieAdapterAnime

        // Observe data
        viewModel.popularMovies.observe(viewLifecycleOwner) { movieAdapterPopular.submitList(it) }
        viewModel.khmerMovies.observe(viewLifecycleOwner) { movieAdapterKhmer.submitList(it) }
        viewModel.animeMovies.observe(viewLifecycleOwner) { movieAdapterAnime.submitList(it) }

        // Category buttons list
        val categoryButtons = listOf(
            binding.btnAction,
            binding.btnComedy,
            binding.btnDrama,
            binding.btnRomance,
            binding.btnHorror
        )

        // Initialize: no selection, transparent background, white text
        categoryButtons.forEach {
            it.isSelected = false
            updateButtonStyle(it, false)
        }

        // Set click listeners for category buttons
        categoryButtons.forEach { button ->
            button.setOnClickListener {
                categoryButtons.forEach { btn ->
                    btn.isSelected = false
                    updateButtonStyle(btn, false)
                }
                button.isSelected = true
                updateButtonStyle(button, true)

                // TODO: Add category filtering logic here
            }
        }
        binding.profileIcon.setOnClickListener {
            val user = FirebaseAuth.getInstance().currentUser
            if (user != null) {
                // User is logged in -> go to Profile fragment showing profile info
                findNavController().navigate(R.id.action_homeFragment_to_profileSuccessFragment)
            } else {
                // User not logged in -> go to Profile fragment showing signup/login UI
                findNavController().navigate(R.id.action_homeFragment_to_profileFragment)
            }
        }

    }



    private fun updateButtonStyle(button: Button, selected: Boolean) {
        if (selected) {
            button.setBackgroundColor(resources.getColor(android.R.color.holo_red_dark, null))
            button.setTextColor(resources.getColor(android.R.color.white, null))
        } else {
            button.setBackgroundColor(resources.getColor(android.R.color.transparent, null))
            button.setTextColor(resources.getColor(android.R.color.white, null))
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
        sliderHandler.removeCallbacks(sliderRunnable)
        _binding = null
    }
}
