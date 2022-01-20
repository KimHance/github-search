package com.example.githubsearchapp.ui.main

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.RecyclerView
import com.example.githubsearchapp.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@AndroidEntryPoint
@ExperimentalCoroutinesApi
@FlowPreview
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var imm: InputMethodManager
    private val viewModel: MainViewModel by viewModels()

    private lateinit var mainPagingAdapter: MainPagingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        mainPagingAdapter = MainPagingAdapter()
        setupViews()
    }

    private fun setupViews() {
        binding.rvMain.adapter = mainPagingAdapter.withLoadStateFooter(
            footer = LoadStateAdapter { mainPagingAdapter.retry() }
        )

        binding.rvMain.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
                binding.etSearch.clearFocus()
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })

        mainPagingAdapter.addLoadStateListener { loadState ->
            binding.tvNothing.isVisible =
                loadState.refresh is LoadState.NotLoading && mainPagingAdapter.itemCount == 0
        }

        lifecycleScope.launch {
            mainPagingAdapter.loadStateFlow
                .distinctUntilChangedBy { it.refresh }
                .filter { it.refresh is LoadState.NotLoading }
                .collect { binding.rvMain.scrollToPosition(0) }
        }
        lifecycleScope.launch {
            binding.etSearch.getQueryTextChangeStateFlow()
                .debounce(500)
                .flatMapLatest { query ->
                    viewModel.getRepoList(query)
                }.collectLatest {
                    mainPagingAdapter.submitData(it)
                }
        }
    }

    private fun EditText.getQueryTextChangeStateFlow(): StateFlow<String> {
        val query = MutableStateFlow("")

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                s: CharSequence?,
                start: Int,
                before: Int,
                count: Int,
            ) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.toString().isEmpty()) {
                    mainPagingAdapter.submitData(lifecycle, PagingData.empty())
                } else {
                    query.value = s.toString()
                }
            }

            override fun afterTextChanged(s: Editable?) {
            }

        })
        return query
    }
}