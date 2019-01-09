package naoms.client.ui.ground.view.contextmenu;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.taocares.naoms.client.model.common.constants.FimsConst;
import com.taocares.naoms.client.model.common.util.BaseMenuItem;
import com.taocares.naoms.client.model.common.util.ButtonAuthConstant;
import com.taocares.naoms.client.model.common.util.Callback;
import com.taocares.naoms.client.model.common.util.MessageDialogInfo;
import com.taocares.naoms.client.model.common.util.MonitoredTask;
import com.taocares.naoms.client.model.common.util.TaskUtil;
import com.taocares.naoms.fims.client.remote.service.FimsClientData;
import com.taocares.naoms.fims.client.remote.service.FimsServiceFactory;
import com.taocares.naoms.fims.dto.config.DispatchDto;
import com.taocares.naoms.fims.dto.config.UserDto;
import com.taocares.naoms.fims.dto.flight.FlightDto;
import com.taocares.naoms.fims.dto.flight.FlightRouteDto;
import com.taocares.naoms.fims.dto.gos.DispatchResultDto;
import com.taocares.naoms.service.BizException;
import com.taocares.naoms.service.IFimsConfigQueryService;
import com.taocares.naoms.service.IFimsDynQueryService;
import com.taocares.naoms.service.IGosQueryService;
import com.taocares.naoms.service.IGosService;

import naoms.client.model.ground.vo.DispathResultVo;
import naoms.client.model.ground.vo.GetDispatchResultVo;
import naoms.client.model.ground.vo.GroundVO;

/**
 *
 * @description : 地面保障右键-值机截止
 * @author LFX
 * @date 2017年12月19日 16:39:38
 *
 */
public class GroundCkiEndMenuItem extends BaseMenuItem {
	private List<DispatchResultDto> dispatchResultDtos = new ArrayList<>();
	private List<DispatchDto> dispatchDtosForDep = new ArrayList<>();
	private List<DispatchDto> dispatchDtos = new ArrayList<DispatchDto>();
	private String key = "";
	private boolean state;
	private GroundVO selectVO;
	private FlightRouteDto flightRouteDto;

	public GroundCkiEndMenuItem(String textString, String key) {
		super(textString);
		this.key = key;
	}

	public GroundCkiEndMenuItem(String textString) {
		super(textString);
	}

	@Override
	public void setAuth() {
		// if ("outport".equals(key)) {
		// setAuthString(ButtonAuthConstant.GOS_COMMAND_FINISH_CHECKIN_OUT);
		// return;
		// }
		// if ("Associated".equals(key)) {
		// setAuthString(ButtonAuthConstant.GOS_COMMAND_FINISH_CHECKIN_CONN);
		// return;
		// }
		setAuthString(ButtonAuthConstant.GOS_COMMAND_FINISH_CHECKIN_CONN);
		return;

	}

	@Override
	protected void setOnClicked() {
		setOnAction(e -> {
			selectVO = (GroundVO) getTableView().getSelectionModel().getSelectedItem();
			flightRouteDto = selectVO.getFrd();
			if(FimsClientData.userHasFunction(ButtonAuthConstant.DISPATCH_FINISHFLIGHTCANOPERATION_CONN)){
				// 若出港航班已起飞，保障环节不可操作
				if (selectVO.getDepartureFlightDto() != null && "起飞".equals(selectVO.getDepartureFlightDto().getDepStatus())) {
					MessageDialogInfo.openInformation("提示", "该出港航班已起飞，不能操作此环节！");
					return;
				}
			}
			// 验证
			run();
			// 保障环节
			if (state) {
				return;
			}
			List<DispathResultVo> dispathResultVos = getColumnNameDispatchByVo(selectVO, FimsConst.DISPATCH_CKIC);
			if (dispathResultVos != null && dispathResultVos.size() > 0) {
				for (DispathResultVo dispatchResultVo : dispathResultVos) {
					if (!isNumeric(dispatchResultVo.getDispathName().substring(0, 1))) {
						// 如果没有上报时间有环节直接上报并改变状态
						if (dispatchResultVo.getDispatchResultDto().getRealEnd() == null) {
							okPressed(dispatchResultVo.getDispatchResultDto());
							break;
						} else {
							// 有本站登机并有上报时间提示
							MessageDialogInfo.openInformation("提示", "值机已截止，若发布多次值机截止请到右侧操作！");
							break;
						}
					}
				}

			} else {
				MessageDialogInfo.openConfirmation("提示：", "未找到指定保障环节,是否创建并上报结束？", new Callback() {

					@Override
					public void call() {
						createDispath();
					}
				});
			}
		});
	}

	public void run() {
		if (flightRouteDto != null) {
			if (flightRouteDto.getDepFlightId() == null) {
				openDialog("FLIGHTTYPE");
				state = true;
				return;
			} else if (FimsConst.P_F_CKO.equals(flightRouteDto.getDepStatus())) {
				openDialog("REPEAT");
				state = true;
				return;
			} else if (flightRouteDto.getCkiCounter() == null || "".equals(flightRouteDto.getCkiCounter())) {
				// 未分配值机不允许发布值机截止
				openDialog("NORESOURCE");
				state = true;
				return;
			}
		}
	}

	/**
	 * 打开消息提示框
	 *
	 * @param type
	 *            提示框类型
	 */
	private void openDialog(String type) {
		switch (type) {
		case "REPEAT":
			MessageDialogInfo.openWarning("提示", "出港航班状态已经是值机截止，请确认！");
			break;
		case "FLIGHTTYPE":
			MessageDialogInfo.openWarning("提示", "只有出港航班才可发布值机截止！");
			break;
		case "NORESOURCE":
			MessageDialogInfo.openWarning("提示", "分配值机柜台后才可发布值机截止！");
			break;
		}

	}

	/**
	 * 过滤值机
	 *
	 * @param vo
	 * @param dispatchName
	 * @return
	 */
	public List<DispatchResultDto> getColumnNameDispatchByDto(List<DispatchResultDto> dispatchResultDtos,
			String dispatchName) {
		List<DispatchResultDto> list = new ArrayList<>();
		if (dispatchResultDtos != null) {
			for (DispatchResultDto dto : dispatchResultDtos) {
				if (dto.getDispatchName().equals(dispatchName)) {
					list.add(dto);
				}
			}
		}
		return list;
	}

	public List<DispathResultVo> getColumnNameDispatchByVo(GroundVO vo, String dispatchName) {
		List<DispathResultVo> list = new ArrayList<>();
		if (vo.getDepartureFlightDto() != null) {
			for (DispatchResultDto dto : vo.getDepartureFlightDto().getDispatchResultDtos()) {
				if (dto.getDispatchName().equals(dispatchName)) {
					DispathResultVo reslutVo = GetDispatchResultVo.getVoWidthGroundVo(dto, vo);
					list.add(reslutVo);
				}
			}
		}
		return list;
	}

	/**
	 * 创建保障环节
	 *
	 */
	public void createDispath() {
		TaskUtil.executeTaskWithDialog(new MonitoredTask<Void>() {

			@Override
			protected Void call() {
				try {
					FlightDto flightDto = FimsServiceFactory.getService(IFimsDynQueryService.class)
							.getFlightDtoByFlightId(selectVO.getDepartureFlightDto().getId());
					dispatchDtosForDep = FimsServiceFactory.getService(IFimsConfigQueryService.class)
							.findDispatchFilterByContract(flightDto.isArrFlight(), flightDto.getAirlineIata());
					for (DispatchDto dispatchDto : dispatchDtosForDep) {
						if (FimsConst.DISPATCH_CKIC.equals(dispatchDto.getDisplayName())) {
							dispatchDtos.add(dispatchDto);
						}
					}
					if (dispatchDtos == null || dispatchDtos.size() == 0) {
						MessageDialogInfo.openInformation("提示", "此航班无值机截止环节!");
						return null;
					}
					// 创建值机
					FimsServiceFactory.getService(IGosService.class)
							.manufactureFlightArrGosTask(selectVO.getDepartureFlightDto().getId(), dispatchDtos);
					dispatchResultDtos = FimsServiceFactory.getService(IGosQueryService.class)
							.findDispatchForReportByFlightId(selectVO.getDepartureFlightDto().getId());
					List<DispatchResultDto> saveDepartureResultDtos = getColumnNameDispatchByDto(dispatchResultDtos,
							FimsConst.DISPATCH_CKIC);
					// 只有一条直接创建
					saveDepartureResultDtos.get(0).setUserByEndReporterId(FimsClientData.getCurrentUser().getId());
					saveDepartureResultDtos.get(0).setUserByEndReporterName(FimsClientData.getCurrentUser().getName());
					saveDepartureResultDtos.get(0).setRealStart(FimsConst.SERVICE_TIME_SECOND());
					FimsServiceFactory.getService(IGosService.class).reportedDispatchInfo(saveDepartureResultDtos.get(0), null,
							FimsConst.OPTION_TYPE_END);
					MessageDialogInfo.openInformation("值机截止上报成功！");
				} catch (BizException e1) {
					e1.printStackTrace();
					MessageDialogInfo.openError("创建失败" + e1.getMessage());
				} catch (Exception e) {
					e.printStackTrace();
					MessageDialogInfo.openError("上报失败，请重试！");
				}
				return null;
			}
		});

	}

	/**
	 * 发布值机截止
	 */
	private void okPressed(DispatchResultDto dto) {
		Date reportTime = FimsConst.SERVICE_TIME_SECOND();
		UserDto userDto = FimsClientData.getCurrentUser();
		dto.setRealEnd(reportTime);
		dto.setUserByEndReporterId(userDto.getId());
		dto.setUserByEndReporterName(userDto.getName());
		TaskUtil.executeTaskWithDialog(new MonitoredTask<Void>() {

			@Override
			protected Void call() {
				try {
					FimsServiceFactory.getService(IGosService.class).reportedDispatchInfo(dto, null,
							FimsConst.OPTION_TYPE_END);
					//MessageDialogInfo.openInformation("值机截止上报成功！");
				} catch (Exception e) {
					MessageDialogInfo.openError("上报失败，请重试！");
				}
				return null;
			}

			@Override
			protected void succeeded() {
				super.succeeded();
			}

		});
	}

	/**
	 * 判断字符串是不是只是为数字
	 *
	 * @param str
	 * @return
	 */
	public boolean isNumeric(String str) {
		Pattern pattern = Pattern.compile("[0-9]*");
		Matcher isNum = pattern.matcher(str);
		if (!isNum.matches()) {
			return false;
		}
		return true;
	}
}
