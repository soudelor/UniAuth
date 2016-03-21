define(['../../../utils/constant'], function (constant) {
    var Controller = function ($scope,$uibModalInstance, UserService, AlertService) {
        //-- Variables --//

        $scope.user = {};

        //-- Methods --//
        $scope.cancel = function(){
            $scope.msg = '';
            $uibModalInstance.dismiss();
        }; // end cancel

        $scope.save = function(){
            UserService.addUser($scope.user,
                function(res) {
                    // user add api successed
                    if(res.info) {
                        $scope.msg = res.info[0].msg;
                    } else {
                        AlertService.addAutoDismissAlert(constant.messageType.info, '用户添加成功.');
                        $uibModalInstance.close();
                    }
                }, function(err) {
                    //restful 404 or other not 200+ response
                    console.log(err);
                }
            );
        }; // end save

    };

    return {
        name: "AddUserController",
        fn: ["$scope","$uibModalInstance", "UserService", "AlertService", Controller]
    };

});
