package org.solovyev.android.calculator.wizard;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import com.viewpagerindicator.PageIndicator;
import org.solovyev.android.calculator.BaseActivity;
import org.solovyev.android.calculator.CalculatorApplication;
import org.solovyev.android.calculator.R;
import org.solovyev.android.wizard.*;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class WizardActivity extends BaseActivity implements WizardsAware {
	@Nonnull
	private final WizardUi<WizardActivity> wizardUi = new WizardUi<>(this, this, 0);

	@Nonnull
	private ViewPager pager;

	@Nonnull
	private PagerAdapter pagerAdapter;

	@Nonnull
	private Wizards wizards = CalculatorApplication.getInstance().getWizards();

	public WizardActivity() {
		super(R.layout.cpp_activity_wizard);
	}

	@Nullable
	private AlertDialog dialog;

	@Nonnull
	private final DialogListener dialogListener = new DialogListener();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		wizardUi.onCreate(savedInstanceState);
		getSupportActionBar().hide();
		final ListWizardFlow flow = (ListWizardFlow) wizardUi.getFlow();

		pager = (ViewPager) findViewById(R.id.pager);
		pagerAdapter = new WizardPagerAdapter(flow, getSupportFragmentManager());
		pager.setAdapter(pagerAdapter);
		final PageIndicator titleIndicator = (PageIndicator) findViewById(R.id.pager_indicator);
		titleIndicator.setViewPager(pager);
		titleIndicator.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				final WizardStep step = flow.getStepAt(position);
				wizardUi.setStep(step);
				wizardUi.getWizard().saveLastStep(step);
			}
		});

		if (savedInstanceState == null) {
			final int position = flow.getPositionFor(wizardUi.getStep());
			pager.setCurrentItem(position);
		}

		if (wizardUi.getWizard().getLastSavedStepName() == null) {
			wizardUi.getWizard().saveLastStep(wizardUi.getStep());
		}
	}

	@Override
	public void onBackPressed() {
		if (pager.getCurrentItem() == 0) {
			finishWizardAbruptly();
		} else {
			pager.setCurrentItem(pager.getCurrentItem() - 1);
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		wizardUi.onSaveInstanceState(outState);
	}

	@Override
	protected void onPause() {
		super.onPause();
		wizardUi.onPause();
	}

	@Nonnull
	@Override
	public Wizards getWizards() {
		return wizards;
	}

	public void setWizards(@Nonnull Wizards wizards) {
		this.wizards = wizards;
	}

	public void finishWizardAbruptly() {
		finishWizardAbruptly(false);
	}

	public void finishWizardAbruptly(boolean confirmed) {
		if (!confirmed) {
			if (dialog != null) {
				return;
			}

			final AlertDialog.Builder b = new AlertDialog.Builder(this);
			b.setTitle(getString(R.string.wizard_finish_confirmation_title)).
					setMessage(R.string.acl_wizard_finish_confirmation).
					setNegativeButton(R.string.c_no, dialogListener).
					setPositiveButton(R.string.c_yes, dialogListener).
					setOnCancelListener(dialogListener);
			dialog = b.create();
			dialog.setOnDismissListener(dialogListener);
			dialog.show();
			return;
		}

		dismissDialog();
		wizardUi.finishWizardAbruptly();
		finish();
	}

	public void finishWizard() {
		wizardUi.finishWizard();
		finish();
	}

	public boolean canGoNext() {
		final int position = pager.getCurrentItem();
		return position != pagerAdapter.getCount() - 1;
	}

	public boolean canGoPrev() {
		final int position = pager.getCurrentItem();
		return position != 0;
	}

	public void goNext() {
		final int position = pager.getCurrentItem();
		if (position < pagerAdapter.getCount() - 1) {
			pager.setCurrentItem(position + 1, true);
		}
	}

	public void goPrev() {
		final int position = pager.getCurrentItem();
		if (position > 0) {
			pager.setCurrentItem(position - 1, true);
		}
	}

	@Override
	protected void onDestroy() {
		dismissDialog();
		super.onDestroy();
	}

	private void dismissDialog() {
		if (dialog != null) {
			dialog.dismiss();
			dialog = null;
		}
	}

	private class WizardPagerAdapter extends FragmentStatePagerAdapter {
		@Nonnull
		private final ListWizardFlow flow;

		public WizardPagerAdapter(@Nonnull ListWizardFlow flow, @Nonnull FragmentManager fm) {
			super(fm);
			this.flow = flow;
		}

		@Override
		public Fragment getItem(int position) {
			final WizardStep step = flow.getStepAt(position);
			final String className = step.getFragmentClass().getName();
			final Bundle args = step.getFragmentArgs();
			return Fragment.instantiate(WizardActivity.this, className, args);
		}

		@Override
		public int getCount() {
			return flow.getCount();
		}
	}

	private class DialogListener implements DialogInterface.OnClickListener, DialogInterface.OnDismissListener, DialogInterface.OnCancelListener {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			if (which == DialogInterface.BUTTON_POSITIVE) {
				finishWizardAbruptly(true);
			}
		}

		public void onDismiss(DialogInterface d) {
			dialog = null;
		}

		@Override
		public void onCancel(DialogInterface d) {
			dialog = null;
		}
	}
}
