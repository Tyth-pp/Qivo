import request from './request'
export const adminAccounts=(params:Record<string,any>)=>request.get('/admin/admin/list',{params})
export const createAdminAccount=(data:Record<string,any>)=>request.post('/admin/admin/add',data)
export const editAdminAccount=(id:number,data:Record<string,any>)=>request.put('/admin/admin/'+id,data)
export const changeAdminStatus=(id:number,status:number)=>request.put('/admin/admin/'+id+'/status',{status})
export const resetAdminPassword=(id:number,password:string)=>request.put('/admin/admin/'+id+'/password',{password})
