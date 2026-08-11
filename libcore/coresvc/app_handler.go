package coresvc

type AppHandler interface {
	OnShowWindow()
	OnDispatchDeepLinks(links []string)
	OnRunTask(taskID string)
}
